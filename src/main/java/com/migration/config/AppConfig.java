package com.migration.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class AppConfig {
    private static final Logger logger = LoggerFactory.getLogger(AppConfig.class);
    private final Properties properties;

    public AppConfig(String propertiesFile) {
        this.properties = new Properties();
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(propertiesFile)) {
            if (input == null) {
                throw new IllegalArgumentException("Unable to find " + propertiesFile);
            }
            properties.load(input);
            logger.info("Configuration loaded successfully from {}", propertiesFile);
            validateRequiredProperties();
        } catch (IOException e) {
            throw new RuntimeException("Failed to load configuration from " + propertiesFile, e);
        }
    }

    private void validateRequiredProperties() {
        // Box: Either developer token OR config file must be set
        boolean hasBoxAuth = (getProperty("box.developer.token") != null && !getProperty("box.developer.token").isEmpty() && !getProperty("box.developer.token").equals("YOUR_BOX_DEV_TOKEN"))
                          || (getProperty("box.config.file") != null && !getProperty("box.config.file").isEmpty());

        if (!hasBoxAuth) {
            logger.warn("Box authentication not configured. Set either box.developer.token or box.config.file");
        }

        // Google: credentials file required
        if (getProperty("google.credentials.file") == null || getProperty("google.credentials.file").isEmpty()) {
            logger.warn("Required property 'google.credentials.file' is not set or empty");
        }

        // Other required properties
        String[] requiredProps = {"db.path", "csv.input.path", "thread.pool.size"};
        for (String prop : requiredProps) {
            if (getProperty(prop) == null || getProperty(prop).isEmpty()) {
                logger.warn("Required property '{}' is not set or empty", prop);
            }
        }
    }

    public String getProperty(String key) {
        String value = properties.getProperty(key);
        if (value != null && value.startsWith("${") && value.endsWith("}")) {
            String envVar = value.substring(2, value.length() - 1);
            String envValue = System.getenv(envVar);
            return envValue != null ? envValue : value;
        }
        return value;
    }

    public String getProperty(String key, String defaultValue) {
        String value = getProperty(key);
        return value != null ? value : defaultValue;
    }

    public int getIntProperty(String key, int defaultValue) {
        String value = getProperty(key);
        try {
            return value != null ? Integer.parseInt(value) : defaultValue;
        } catch (NumberFormatException e) {
            logger.warn("Invalid integer value for property '{}': {}. Using default: {}",
                       key, value, defaultValue);
            return defaultValue;
        }
    }

    public long getLongProperty(String key, long defaultValue) {
        String value = getProperty(key);
        try {
            return value != null ? Long.parseLong(value) : defaultValue;
        } catch (NumberFormatException e) {
            logger.warn("Invalid long value for property '{}': {}. Using default: {}",
                       key, value, defaultValue);
            return defaultValue;
        }
    }

    public String getBoxClientId() {
        return getProperty("box.client.id");
    }

    public String getBoxClientSecret() {
        return getProperty("box.client.secret");
    }

    public String getBoxDeveloperToken() {
        return getProperty("box.developer.token");
    }

    public String getBoxEnterpriseId() {
        return getProperty("box.enterprise.id");
    }

    public String getBoxConfigFile() {
        return getProperty("box.config.file");
    }

    public String getBoxAsUserId() {
        return getProperty("box.as.user.id");
    }

    public String getGoogleCredentialsFile() {
        return getProperty("google.credentials.file");
    }

    public String getGoogleApplicationName() {
        return getProperty("google.application.name", "Box-Google-Converter");
    }

    public String getGoogleAuthType() {
        return getProperty("google.auth.type", "service_account");
    }

    public boolean isOAuthMode() {
        return "oauth".equalsIgnoreCase(getGoogleAuthType());
    }

    public boolean isServiceAccountMode() {
        return "service_account".equalsIgnoreCase(getGoogleAuthType());
    }

    public String getGoogleOAuthRedirectUri() {
        return getProperty("google.oauth.redirect.uri", "http://localhost:8080/oauth2callback");
    }

    /** Default Workspace user to impersonate when CSV user_email is blank or "ignored". */
    public String getGoogleImpersonateUser() {
        return getProperty("google.impersonate.user");
    }

    public String getDbPath() {
        return getProperty("db.path", "./migration-results.db");
    }

    public int getThreadPoolSize() {
        return getIntProperty("thread.pool.size", 100);
    }

    public int getThreadPoolMaxSize() {
        return getIntProperty("thread.pool.max.size", 500);
    }

    public String getCsvInputPath() {
        return getProperty("csv.input.path", "./migration-input.csv");
    }

    public int getRetryMaxAttempts() {
        return getIntProperty("retry.max.attempts", 3);
    }

    public long getRetryDelaySeconds() {
        return getLongProperty("retry.delay.seconds", 5);
    }
}
