package com.migration.service;

import com.migration.config.AppConfig;
import com.migration.model.MigrationRecord;
import com.migration.model.MigrationStatus;
import com.migration.repository.MigrationRepository;
import com.migration.util.CsvReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class MigrationOrchestrator {
    private static final Logger logger = LoggerFactory.getLogger(MigrationOrchestrator.class);

    private final GoogleDriveService driveService;
    private final MigrationRepository repository;
    private final AppConfig config;
    private final JobLauncher jobLauncher;
    private final JobOperator jobOperator;
    private final Job migrationJob;

    private volatile JobExecution currentExecution;

    public MigrationOrchestrator(GoogleDriveService driveService, MigrationRepository repository,
                                AppConfig config, JobLauncher jobLauncher, JobOperator jobOperator,
                                Job migrationJob) {
        this.driveService = driveService;
        this.repository = repository;
        this.config = config;
        this.jobLauncher = jobLauncher;
        this.jobOperator = jobOperator;
        this.migrationJob = migrationJob;
    }

    public void startMigration() {
        logger.info("========================================");
        logger.info("Starting Box Office-to-Decoupled-Docs Migration (Spring Batch)");
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

            logger.info("Launching Spring Batch job...");
            JobParameters jobParameters = new JobParametersBuilder()
                    .addLong("time", System.currentTimeMillis())
                    .toJobParameters();

            currentExecution = jobLauncher.run(migrationJob, jobParameters);

            logger.info("Spring Batch job finished with status: {}", currentExecution.getStatus());
            currentExecution = null;
            printMigrationSummary();

        } catch (Exception e) {
            logger.error("Migration orchestration failed", e);
            throw new RuntimeException("Migration orchestration failed", e);
        }
    }

    public boolean stopMigration() {
        JobExecution execution = currentExecution;
        if (execution == null || !execution.isRunning()) {
            logger.warn("Stop requested but no migration is currently running");
            return false;
        }

        try {
            logger.info("Stopping migration job (execution ID: {})", execution.getId());
            jobOperator.stop(execution.getId());
            logger.info("Stop signal sent. Job will stop after current chunk completes.");
            return true;
        } catch (Exception e) {
            logger.error("Failed to stop migration job", e);
            return false;
        }
    }

    public boolean isRunning() {
        JobExecution execution = currentExecution;
        return execution != null && execution.isRunning();
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
        long downloading = stats.getOrDefault(MigrationStatus.DOWNLOADING, 0L);
        long converting = stats.getOrDefault(MigrationStatus.CONVERTING, 0L);
        long exporting = stats.getOrDefault(MigrationStatus.EXPORTING, 0L);
        long uploading = stats.getOrDefault(MigrationStatus.UPLOADING, 0L);

        // Sum up active tasks
        long active = inProgress + downloading + converting + exporting + uploading;

        logger.info("Total Records:     {}", total);
        logger.info("Completed:         {}", completed);
        logger.info("Failed:            {}", failed);
        logger.info("Pending:           {}", pending);
        logger.info("Active/Running:    {}", active);

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
