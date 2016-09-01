package com.upside.dropwizard.commands;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Created by bsiemon on 9/1/16.
 */
public class BaseConfiguration {
    private final String tier;
    private final String bucketPrefix;
    private final String version;

    private BaseConfiguration(String tier, String bucketPrefix, String version) {
        this.tier = tier;
        this.bucketPrefix = bucketPrefix;
        this.version = version;
    }

    public BaseConfiguration create(@JsonProperty("tier") String tier,
                                    @JsonProperty("bucketPrefix") String bucketPrefix,
                                    @JsonProperty("version") String version) {
        return new BaseConfiguration(tier, bucketPrefix, version);
    }

    public String getTier() {
        return tier;
    }

    public String getBucket() {
        return bucketPrefix;
    }

    public String getVersion() {
        return version;
    }

    public String buildS3Key(String name) {
        return String.format("service/configuration/%s/%s/%s/configuration.yml",
                name, tier, version);
    }
}
