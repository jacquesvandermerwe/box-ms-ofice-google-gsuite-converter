package com.migration;

import com.box.sdk.BoxAPIConnection;
import com.migration.config.AppConfig;
import com.migration.config.CredentialsManager;
import com.migration.repository.MigrationRepository;
import com.migration.service.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        logger.info("Box to Google Drive Migration Tool - Starting...");

        try {
            logger.info("Loading configuration...");
            AppConfig config = new AppConfig("application.properties");

            logger.info("Initializing credentials...");
            CredentialsManager credentialsManager = new CredentialsManager(config);
            BoxAPIConnection boxApi = credentialsManager.getBoxConnection();

            logger.info("Initializing database...");
            MigrationRepository repository = new MigrationRepository(config.getDbPath());
            repository.initialize();

            logger.info("Initializing services...");
            BoxService boxService = new BoxService(boxApi, config.getBoxAsUserId());
            GoogleDriveService googleDriveService = new GoogleDriveService(credentialsManager);
            ConversionService conversionService = new ConversionService(credentialsManager);

            logger.info("Starting migration orchestrator...");
            MigrationOrchestrator orchestrator = new MigrationOrchestrator(
                    boxService,
                    googleDriveService,
                    conversionService,
                    repository,
                    config
            );

            orchestrator.startMigration();

            logger.info("Migration process completed.");
            System.exit(0);

        } catch (Exception e) {
            logger.error("Migration failed with error", e);
            System.err.println("Migration failed: " + e.getMessage());
            System.exit(1);
        }
    }
}
