package com.migration;

import com.box.sdk.BoxAPIConnection;
import com.migration.config.AppConfig;
import com.migration.config.CredentialsManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
@EnableConfigurationProperties(AppConfig.class)
public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        logger.info("Box to Google Drive Migration Tool - Starting Spring Boot Application...");
        SpringApplication.run(Main.class, args);
    }

    @Bean
    public BoxAPIConnection boxAPIConnection(CredentialsManager credentialsManager) {
        return credentialsManager.getBoxConnection();
    }
}
