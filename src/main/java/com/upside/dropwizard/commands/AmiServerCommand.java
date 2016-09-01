package com.upside.dropwizard.commands;


import com.amazonaws.services.s3.AmazonS3Client;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.dropwizard.Application;
import io.dropwizard.Configuration;
import io.dropwizard.cli.ServerCommand;
import io.dropwizard.configuration.ConfigurationException;
import io.dropwizard.configuration.ConfigurationFactory;
import io.dropwizard.configuration.ConfigurationFactoryFactory;
import io.dropwizard.configuration.ConfigurationSourceProvider;
import io.dropwizard.setup.Bootstrap;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.validation.Validator;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;

/**
 * Starts server on an upside ami based on the conventions here:
 * <p>
 * <link to ami conventions>
 * <p>
 * <p>
 * Your AMI must have permission via EC2 role to access the service configuration bucket.
 */
public class AmiServerCommand<T extends Configuration> extends ServerCommand<T> {

    private static final Logger LOGGER = LoggerFactory.getLogger(AmiServerCommand.class);

    private boolean asynchronous;

    private T configuration;

    private AmazonS3Client s3Client = new AmazonS3Client();

    public AmiServerCommand(Application<T> application) {
        super(application, "ami-server", "Runs the Dropwizard application as an" +
                " HTTP server on the upside service ami");
    }

    /**
     * Configure the command's {@link Subparser}. <p><strong> N.B.: if you override this method, you
     * <em>must</em> call {@code super.override(subparser)} in order to preserve the configuration
     * file parameter in the subparser. </strong></p>
     *
     * @param subparser the {@link Subparser} specific to the command
     */
    @Override
    public void configure(Subparser subparser) {
        subparser.addArgument("version")
                .nargs("?")
                .help("version of this deployment");
        subparser.addArgument("tier")
                .nargs("?")
                .help("tier of this deployment");
    }

    @Override
    @SuppressWarnings("unchecked")
    public void run(Bootstrap<?> wildcardBootstrap, Namespace namespace) throws Exception {
        final Bootstrap<T> bootstrap = (Bootstrap<T>) wildcardBootstrap;

        // get base s3 bucket and key from environment
        File baseConfigurationFile = new File("/etc/upside/service/base.json");
        if (!baseConfigurationFile.exists()) {
            throw new RuntimeException("Base configuration file not found.");
        }
        BaseConfiguration baseConfiguration = bootstrap.getObjectMapper()
                .readerFor(BaseConfiguration.class)
                .readValue(baseConfigurationFile);

        //Get the file from s3
        String name = bootstrap.getApplication().getName();
        String key = baseConfiguration.buildS3Key(bootstrap.getApplication().getName());

        File targetPath = new File(String.format("/etc/upside/service/%s/%s/%s/config.yml",
                name,
                baseConfiguration.getVersion(),
                baseConfiguration.getTier()));

        if (!targetPath.exists()) {
            try (InputStream in = s3Client.getObject(baseConfiguration.getBucket(), key)
                    .getObjectContent()) {
                Files.copy(in, targetPath.toPath());
            }
        }

        configuration = parseConfiguration(bootstrap.getConfigurationFactoryFactory(),
                bootstrap.getConfigurationSourceProvider(),
                bootstrap.getValidatorFactory().getValidator(),
                targetPath.getCanonicalPath(),
                getConfigurationClass(),
                bootstrap.getObjectMapper());

        try {
            if (configuration != null) {
                configuration.getLoggingFactory().configure(bootstrap.getMetricRegistry(),
                        bootstrap.getApplication().getName());
            }

            run(bootstrap, namespace, configuration);
        }
        finally {
            if (!asynchronous) {
                cleanup();
            }
        }
    }

    private T parseConfiguration(ConfigurationFactoryFactory<T> configurationFactoryFactory,
                                 ConfigurationSourceProvider provider,
                                 Validator validator,
                                 String path,
                                 Class<T> klass,
                                 ObjectMapper objectMapper) throws IOException, ConfigurationException {
        final ConfigurationFactory<T> configurationFactory = configurationFactoryFactory
                .create(klass, validator, objectMapper, "dw");
        if (path != null) {
            return configurationFactory.build(provider, path);
        }
        return configurationFactory.build();
    }

    protected void cleanupAsynchronously() {
        this.asynchronous = true;
    }

    protected void cleanup() {
        if (configuration != null) {
            configuration.getLoggingFactory().stop();
        }
    }
}
