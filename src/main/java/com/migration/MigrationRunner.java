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
            logger.info("Database initialized successfully. Web server running on http://localhost:8080");
            logger.info("Dashboard available at: http://localhost:8080/");
            logger.info("API status endpoint: http://localhost:8080/api/status");
            logger.info("To start migration, access the dashboard or call the migration endpoint");

            // Do NOT auto-start migration - keep web server running
            // Migration can be triggered via REST endpoint or dashboard
        } catch (Exception e) {
            logger.error("Initialization failed with error", e);
            System.err.println("Initialization failed: " + e.getMessage());
            System.exit(1);
        }
    }
}
