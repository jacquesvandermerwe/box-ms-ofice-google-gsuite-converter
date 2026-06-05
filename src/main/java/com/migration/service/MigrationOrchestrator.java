package com.migration.service;

import com.migration.config.AppConfig;
import com.migration.model.MigrationRecord;
import com.migration.model.MigrationStatus;
import com.migration.processor.MigrationTaskProcessor;
import com.migration.repository.MigrationRepository;
import com.migration.util.CsvReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

public class MigrationOrchestrator {
    private static final Logger logger = LoggerFactory.getLogger(MigrationOrchestrator.class);

    private final BoxService boxService;
    private final GoogleDriveService driveService;
    private final ConversionService conversionService;
    private final MigrationRepository repository;
    private final AppConfig config;
    private ExecutorService executorService;

    public MigrationOrchestrator(BoxService boxService, GoogleDriveService driveService,
                                ConversionService conversionService, MigrationRepository repository,
                                AppConfig config) {
        this.boxService = boxService;
        this.driveService = driveService;
        this.conversionService = conversionService;
        this.repository = repository;
        this.config = config;
    }

    public void startMigration() {
        logger.info("========================================");
        logger.info("Starting Box Office-to-Decoupled-Docs Migration");
        logger.info("========================================");

        // Log authentication mode
        if (driveService.isOAuthMode()) {
            logger.info("Authentication Mode: OAuth (Personal Google Account)");
            logger.info("Google conversion uses the authenticated user's Drive (temporary)");
            logger.info("user_email column in CSV will be ignored for Google auth");
        } else {
            logger.info("Authentication Mode: Service Account (Google Workspace)");
            logger.info("Google conversion impersonates CSV user_email (or google.impersonate.user default)");
            logger.info("Domain-wide delegation must be configured in Admin Console");
            logger.info("CSV user_email must be a real @yourdomain.com address — not 'ignored'");
        }
        logger.info("Decoupled exports (.gdoc/.gsheet/.gslides) replace the source Box file as a new version");
        logger.info("========================================");

        try {
            loadCsvRecords();

            List<MigrationRecord> recordsToProcess = repository.getPendingRecords();
            logger.info("Found {} records to process (PENDING or FAILED)", recordsToProcess.size());

            if (recordsToProcess.isEmpty()) {
                logger.info("No records to process. Migration complete.");
                return;
            }

            if (!driveService.isOAuthMode()) {
                driveService.validateServiceAccountImpersonation(recordsToProcess.get(0).getUserEmail());
            }

            int maxConcurrency = config.getThreadPoolSize();
            logger.info("Creating virtual thread executor with max concurrency: {}", maxConcurrency);
            logger.info("Using virtual threads for lightweight, scalable I/O operations");
            executorService = Executors.newVirtualThreadPerTaskExecutor();

            List<Future<MigrationRecord>> futures = new ArrayList<>();

            for (MigrationRecord record : recordsToProcess) {
                MigrationTaskProcessor task = new MigrationTaskProcessor(
                        record, boxService, driveService, conversionService, repository
                );
                Future<MigrationRecord> future = executorService.submit(task);
                futures.add(future);
            }

            logger.info("Submitted {} tasks for processing", futures.size());

            int completed = 0;
            int failed = 0;

            for (Future<MigrationRecord> future : futures) {
                try {
                    MigrationRecord result = future.get();

                    if (result.getStatus() == MigrationStatus.COMPLETED) {
                        completed++;
                        logger.info("✓ Successfully migrated: {}",
                                   result.getBoxFileId());
                    } else {
                        failed++;
                        logger.error("✗ Failed to migrate: {} - {}",
                                    result.getBoxFileId(), result.getErrorMessage());
                    }

                } catch (InterruptedException e) {
                    logger.error("Task interrupted", e);
                    Thread.currentThread().interrupt();
                } catch (ExecutionException e) {
                    failed++;
                    logger.error("Task execution failed", e.getCause());
                }
            }

            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
            }

            logger.info("All tasks completed. Completed: {}, Failed: {}", completed, failed);
            printMigrationSummary();

        } catch (Exception e) {
            logger.error("Migration orchestration failed", e);
            throw new RuntimeException("Migration orchestration failed", e);
        }
    }

    private void loadCsvRecords() {
        logger.info("Loading records from CSV: {}", config.getCsvInputPath());

        List<MigrationRecord> csvRecords = CsvReader.readCsv(config.getCsvInputPath());
        logger.info("Read {} records from CSV", csvRecords.size());

        int newRecords = 0;
        int existingRecords = 0;

        for (MigrationRecord record : csvRecords) {
            MigrationRecord existing = repository.getRecordByBoxFileId(record.getBoxFileId());

            if (existing == null) {
                record.setStatus(MigrationStatus.PENDING);
                repository.insertOrUpdateRecord(record);
                newRecords++;
            } else {
                existingRecords++;
                logger.debug("Record already exists in database: {}", record.getBoxFileId());
            }
        }

        logger.info("CSV processing complete. New records: {}, Existing records: {}",
                   newRecords, existingRecords);
    }

    private void printMigrationSummary() {
        logger.info("========================================");
        logger.info("Migration Summary");
        logger.info("========================================");

        Map<MigrationStatus, Long> stats = repository.getStatistics();

        long total = stats.values().stream().mapToLong(Long::longValue).sum();
        long completed = stats.getOrDefault(MigrationStatus.COMPLETED, 0L);
        long failed = stats.getOrDefault(MigrationStatus.FAILED, 0L);
        long pending = stats.getOrDefault(MigrationStatus.PENDING, 0L);
        long inProgress = stats.getOrDefault(MigrationStatus.IN_PROGRESS, 0L);

        logger.info("Total Records:     {}", total);
        logger.info("Completed:         {}", completed);
        logger.info("Failed:            {}", failed);
        logger.info("Pending:           {}", pending);
        logger.info("In Progress:       {}", inProgress);

        if (total > 0) {
            double successRate = (completed * 100.0) / total;
            logger.info("Success Rate:      {}%", String.format("%.2f", successRate));
        }

        logger.info("========================================");

        if (failed > 0) {
            logger.warn("Some migrations failed. Check the logs and database for details.");
            logger.warn("You can re-run the migration to retry failed records.");
        }

        if (completed == total) {
            logger.info("All migrations completed successfully!");
        }
    }
}
