package com.migration;

import com.migration.repository.MigrationRepository;
import com.migration.service.MigrationOrchestrator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!test")
public class MigrationRunner implements CommandLineRunner {
    private static final Logger logger = LoggerFactory.getLogger(MigrationRunner.class);

    private final MigrationRepository repository;
    private final MigrationOrchestrator orchestrator;

    public MigrationRunner(MigrationRepository repository, MigrationOrchestrator orchestrator) {
        this.repository = repository;
        this.orchestrator = orchestrator;
    }

    @Override
    public void run(String... args) {
        try {
            logger.info("Initializing database...");
            repository.initialize();

            logger.info("Starting migration orchestrator...");
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
