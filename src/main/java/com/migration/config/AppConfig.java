package com.migration.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

@Component
public class AppConfig {
    private static final Logger logger = LoggerFactory.getLogger(AppConfig.class);

    private Properties properties; // For manual loading compatibility

    @Value("${box.client.id:}")
    private String boxClientId;

    @Value("${box.client.secret:}")
    private String boxClientSecret;

    @Value("${box.developer.token:}")
    private String boxDeveloperToken;

    @Value("${box.enterprise.id:}")
    private String boxEnterpriseId;

    @Value("${box.config.file:}")
    private String boxConfigFile;

    @Value("${box.as.user.id:}")
    private String boxAsUserId;

    @Value("${google.credentials.file:}")
    private String googleCredentialsFile;

    @Value("${google.application.name:Box-Google-Converter}")
    private String googleApplicationName;

    @Value("${google.auth.type:service_account}")
    private String googleAuthType;

    @Value("${google.oauth.redirect.uri:http://localhost:8080/oauth2callback}")
    private String googleOAuthRedirectUri;

    @Value("${google.impersonate.user:}")
    private String googleImpersonateUser;

    @Value("${db.path:./migration-results.db}")
    private String dbPath;

    @Value("${thread.pool.size:100}")
    private int threadPoolSize;

    @Value("${thread.pool.max.size:500}")
    private int threadPoolMaxSize;

    @Value("${csv.input.path:./migration-input.csv}")
    private String csvInputPath;

    @Value("${retry.max.attempts:3}")
    private int retryMaxAttempts;

    @Value("${retry.delay.seconds:5}")
    private long retryDelaySeconds;

    public AppConfig() {
        // No-arg constructor for Spring
    }

    public AppConfig(String propertiesFile) {
        this.properties = new Properties();
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(propertiesFile)) {
            if (input == null) {
                throw new IllegalArgumentException("Unable to find " + propertiesFile);
            }
            properties.load(input);
            logger.info("Configuration loaded successfully from {}", propertiesFile);
            bindPropertiesToFields();
            validateRequiredProperties();
        } catch (IOException e) {
            throw new RuntimeException("Failed to load configuration from " + propertiesFile, e);
        }
    }

    private void bindPropertiesToFields() {
        this.boxClientId = getProperty("box.client.id");
        this.boxClientSecret = getProperty("box.client.secret");
        this.boxDeveloperToken = getProperty("box.developer.token");
        this.boxEnterpriseId = getProperty("box.enterprise.id");
        this.boxConfigFile = getProperty("box.config.file");
        this.boxAsUserId = getProperty("box.as.user.id");
        this.googleCredentialsFile = getProperty("google.credentials.file");
        this.googleApplicationName = getProperty("google.application.name", "Box-Google-Converter");
        this.googleAuthType = getProperty("google.auth.type", "service_account");
        this.googleOAuthRedirectUri = getProperty("google.oauth.redirect.uri", "http://localhost:8080/oauth2callback");
        this.googleImpersonateUser = getProperty("google.impersonate.user");
        this.dbPath = getProperty("db.path", "./migration-results.db");
        this.threadPoolSize = getIntProperty("thread.pool.size", 100);
        this.threadPoolMaxSize = getIntProperty("thread.pool.max.size", 500);
        this.csvInputPath = getProperty("csv.input.path", "./migration-input.csv");
        this.retryMaxAttempts = getIntProperty("retry.max.attempts", 3);
        this.retryDelaySeconds = getLongProperty("retry.delay.seconds", 5);
    }

    private String getProperty(String key) {
        if (properties == null) return null;
        String value = properties.getProperty(key);
        if (value != null && value.startsWith("${") && value.endsWith("}")) {
            String envVar = value.substring(2, value.length() - 1);
            String envValue = System.getenv(envVar);
            return envValue != null ? envValue : value;
        }
        return value;
    }

    private String getProperty(String key, String defaultValue) {
        String value = getProperty(key);
        return value != null ? value : defaultValue;
    }

    private int getIntProperty(String key, int defaultValue) {
        String value = getProperty(key);
        try {
            return value != null ? Integer.parseInt(value) : defaultValue;
        } catch (NumberFormatException e) {
            logger.warn("Invalid integer value for property '{}': {}. Using default: {}",
                       key, value, defaultValue);
            return defaultValue;
        }
    }

    private long getLongProperty(String key, long defaultValue) {
        String value = getProperty(key);
        try {
            return value != null ? Long.parseLong(value) : defaultValue;
        } catch (NumberFormatException e) {
            logger.warn("Invalid long value for property '{}': {}. Using default: {}",
                       key, value, defaultValue);
            return defaultValue;
        }
    }

    @PostConstruct
    public void validateRequiredProperties() {
        // Box: Either developer token OR config file must be set
        boolean hasBoxAuth = (boxDeveloperToken != null && !boxDeveloperToken.isEmpty() && !boxDeveloperToken.equals("YOUR_BOX_DEV_TOKEN"))
                          || (boxConfigFile != null && !boxConfigFile.isEmpty());

        if (!hasBoxAuth) {
            logger.warn("Box authentication not configured. Set either box.developer.token or box.config.file");
        }

        // Google: credentials file required
        if (googleCredentialsFile == null || googleCredentialsFile.isEmpty()) {
            logger.warn("Required property 'google.credentials.file' is not set or empty");
        }

        // Other required properties
        if (dbPath == null || dbPath.isEmpty()) {
            logger.warn("Required property 'db.path' is not set or empty");
        }
        if (csvInputPath == null || csvInputPath.isEmpty()) {
            logger.warn("Required property 'csv.input.path' is not set or empty");
        }
        if (threadPoolSize <= 0) {
            logger.warn("Required property 'thread.pool.size' must be greater than 0");
        }
    }

    // Getters and Setters
    public String getBoxClientId() { return boxClientId; }
    public void setBoxClientId(String boxClientId) { this.boxClientId = boxClientId; }

    public String getBoxClientSecret() { return boxClientSecret; }
    public void setBoxClientSecret(String boxClientSecret) { this.boxClientSecret = boxClientSecret; }

    public String getBoxDeveloperToken() { return boxDeveloperToken; }
    public void setBoxDeveloperToken(String boxDeveloperToken) { this.boxDeveloperToken = boxDeveloperToken; }

    public String getBoxEnterpriseId() { return boxEnterpriseId; }
    public void setBoxEnterpriseId(String boxEnterpriseId) { this.boxEnterpriseId = boxEnterpriseId; }

    public String getBoxConfigFile() { return boxConfigFile; }
    public void setBoxConfigFile(String boxConfigFile) { this.boxConfigFile = boxConfigFile; }

    public String getBoxAsUserId() { return boxAsUserId; }
    public void setBoxAsUserId(String boxAsUserId) { this.boxAsUserId = boxAsUserId; }

    public String getGoogleCredentialsFile() { return googleCredentialsFile; }
    public void setGoogleCredentialsFile(String googleCredentialsFile) { this.googleCredentialsFile = googleCredentialsFile; }

    public String getGoogleApplicationName() { return googleApplicationName; }
    public void setGoogleApplicationName(String googleApplicationName) { this.googleApplicationName = googleApplicationName; }

    public String getGoogleAuthType() { return googleAuthType; }
    public void setGoogleAuthType(String googleAuthType) { this.googleAuthType = googleAuthType; }

    public boolean isOAuthMode() {
        return "oauth".equalsIgnoreCase(getGoogleAuthType());
    }

    public boolean isServiceAccountMode() {
        return "service_account".equalsIgnoreCase(getGoogleAuthType());
    }

    public String getGoogleOAuthRedirectUri() { return googleOAuthRedirectUri; }
    public void setGoogleOAuthRedirectUri(String googleOAuthRedirectUri) { this.googleOAuthRedirectUri = googleOAuthRedirectUri; }

    public String getGoogleImpersonateUser() { return googleImpersonateUser; }
    public void setGoogleImpersonateUser(String googleImpersonateUser) { this.googleImpersonateUser = googleImpersonateUser; }

    public String getDbPath() { return dbPath; }
    public void setDbPath(String dbPath) { this.dbPath = dbPath; }

    public int getThreadPoolSize() { return threadPoolSize; }
    public void setThreadPoolSize(int threadPoolSize) { this.threadPoolSize = threadPoolSize; }

    public int getThreadPoolMaxSize() { return threadPoolMaxSize; }
    public void setThreadPoolMaxSize(int threadPoolMaxSize) { this.threadPoolMaxSize = threadPoolMaxSize; }

    public String getCsvInputPath() { return csvInputPath; }
    public void setCsvInputPath(String csvInputPath) { this.csvInputPath = csvInputPath; }

    public int getRetryMaxAttempts() { return retryMaxAttempts; }
    public void setRetryMaxAttempts(int retryMaxAttempts) { this.retryMaxAttempts = retryMaxAttempts; }

    public long getRetryDelaySeconds() { return retryDelaySeconds; }
    public void setRetryDelaySeconds(long retryDelaySeconds) { this.retryDelaySeconds = retryDelaySeconds; }
}
