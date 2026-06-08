package com.migration.controller;

import com.migration.config.AppConfig;
import com.migration.model.MigrationRecord;
import com.migration.model.MigrationStatus;
import com.migration.repository.MigrationRepository;
import com.migration.service.MigrationOrchestrator;
import com.migration.util.CsvReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

@RestController
public class MigrationStatusController {
    private static final Logger logger = LoggerFactory.getLogger(MigrationStatusController.class);

    private final MigrationRepository repository;
    private final MigrationOrchestrator orchestrator;
    private final AppConfig config;
    private final AtomicBoolean migrationRunning = new AtomicBoolean(false);
    private volatile String currentCsvPath = null;

    public MigrationStatusController(MigrationRepository repository, MigrationOrchestrator orchestrator, AppConfig config) {
        this.repository = repository;
        this.orchestrator = orchestrator;
        this.config = config;
        this.currentCsvPath = config.getCsvInputPath(); // Initialize with config value
    }

    @GetMapping("/api/status")
    public Map<String, Object> getStatus() {
        Map<MigrationStatus, Long> rawStats = repository.getStatistics();
        List<MigrationRecord> recentRecords = repository.getRecentRecords(15);

        long total = rawStats.values().stream().mapToLong(Long::longValue).sum();
        long completed = rawStats.getOrDefault(MigrationStatus.COMPLETED, 0L);
        long failed = rawStats.getOrDefault(MigrationStatus.FAILED, 0L);
        long pending = rawStats.getOrDefault(MigrationStatus.PENDING, 0L);

        long inProgress = rawStats.getOrDefault(MigrationStatus.IN_PROGRESS, 0L);
        long downloading = rawStats.getOrDefault(MigrationStatus.DOWNLOADING, 0L);
        long converting = rawStats.getOrDefault(MigrationStatus.CONVERTING, 0L);
        long exporting = rawStats.getOrDefault(MigrationStatus.EXPORTING, 0L);
        long uploading = rawStats.getOrDefault(MigrationStatus.UPLOADING, 0L);

        long active = inProgress + downloading + converting + exporting + uploading;

        double successRate = total == 0 ? 0.0 : (completed * 100.0) / total;
        double progress = total == 0 ? 0.0 : ((completed + failed) * 100.0) / total;

        Map<String, Object> stats = new HashMap<>();
        stats.put("total", total);
        stats.put("completed", completed);
        stats.put("failed", failed);
        stats.put("pending", pending);
        stats.put("active", active);
        stats.put("successRate", Math.round(successRate * 10.0) / 10.0);
        stats.put("progress", Math.round(progress * 10.0) / 10.0);
        stats.put("migrationRunning", migrationRunning.get());

        Map<String, Object> response = new HashMap<>();
        response.put("stats", stats);
        response.put("recent", recentRecords);

        return response;
    }

    @PostMapping("/api/migration/start")
    public Map<String, Object> startMigration() {
        if (!migrationRunning.compareAndSet(false, true)) {
            logger.warn("Migration start requested but migration is already running");
            return Map.of(
                "success", false,
                "message", "Migration is already running"
            );
        }

        logger.info("Starting migration via REST API endpoint");

        CompletableFuture.runAsync(() -> {
            try {
                orchestrator.startMigration();
                logger.info("Migration completed successfully via API");
            } catch (Exception e) {
                logger.error("Migration failed via API", e);
            } finally {
                migrationRunning.set(false);
            }
        });

        return Map.of(
            "success", true,
            "message", "Migration started successfully"
        );
    }

    @PostMapping("/api/migration/stop")
    public Map<String, Object> stopMigration() {
        if (!migrationRunning.get()) {
            return Map.of(
                "success", false,
                "message", "No migration is currently running"
            );
        }

        boolean stopped = orchestrator.stopMigration();
        if (stopped) {
            logger.info("Migration stop requested via REST API");
            return Map.of(
                "success", true,
                "message", "Stop signal sent. Migration will stop after current file completes."
            );
        } else {
            return Map.of(
                "success", false,
                "message", "Failed to stop migration"
            );
        }
    }

    @PostMapping("/api/csv/upload")
    public Map<String, Object> uploadCsv(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            logger.warn("CSV upload attempted with empty file");
            return Map.of(
                "success", false,
                "message", "Please select a CSV file to upload"
            );
        }

        String rawFilename = file.getOriginalFilename();
        if (rawFilename == null || !rawFilename.toLowerCase().endsWith(".csv")) {
            logger.warn("CSV upload attempted with non-CSV file: {}", rawFilename);
            return Map.of(
                "success", false,
                "message", "Please upload a valid CSV file"
            );
        }
        String filename = Paths.get(rawFilename).getFileName().toString();

        try {
            // Save uploaded file to a temporary location using absolute path
            Path uploadDir = Paths.get(System.getProperty("user.dir"), "uploads");
            if (!Files.exists(uploadDir)) {
                Files.createDirectories(uploadDir);
                logger.info("Created uploads directory at: {}", uploadDir.toAbsolutePath());
            }

            Path filePath = uploadDir.resolve("uploaded_" + System.currentTimeMillis() + "_" + filename);
            logger.info("Saving uploaded CSV to: {}", filePath.toAbsolutePath());
            file.transferTo(filePath.toFile());

            // Validate CSV by trying to read it
            List<MigrationRecord> records = CsvReader.readCsv(filePath.toString());

            logger.info("CSV file uploaded successfully: {} with {} records", filename, records.size());

            // Update the current CSV path
            currentCsvPath = filePath.toString();

            // Load records into database
            int newRecords = 0;
            int existingRecords = 0;

            for (MigrationRecord record : records) {
                MigrationRecord existing = repository.getRecordByBoxFileId(record.getBoxFileId());

                if (existing == null) {
                    record.setStatus(MigrationStatus.PENDING);
                    repository.insertOrUpdateRecord(record);
                    newRecords++;
                } else {
                    existingRecords++;
                }
            }

            return Map.of(
                "success", true,
                "message", "CSV uploaded successfully",
                "filename", filename,
                "totalRecords", records.size(),
                "newRecords", newRecords,
                "existingRecords", existingRecords,
                "filePath", filePath.toString()
            );

        } catch (IOException e) {
            logger.error("Failed to upload CSV file", e);
            return Map.of(
                "success", false,
                "message", "Failed to upload file: " + e.getMessage()
            );
        } catch (Exception e) {
            logger.error("Failed to process CSV file", e);
            return Map.of(
                "success", false,
                "message", "Failed to process CSV file: " + e.getMessage()
            );
        }
    }

    @GetMapping("/api/csv/current")
    public Map<String, Object> getCurrentCsv() {
        String csvPath = currentCsvPath != null ? currentCsvPath : config.getCsvInputPath();
        Path path = Paths.get(csvPath);

        return Map.of(
            "csvPath", csvPath,
            "exists", Files.exists(path),
            "source", currentCsvPath != null ? "uploaded" : "config"
        );
    }

    @GetMapping("/api/records")
    public Map<String, Object> getRecords(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search) {

        if (page < 0) page = 0;
        if (size <= 0) size = 50;
        int offset = page * size;
        List<MigrationRecord> records = repository.getRecordsPaginated(offset, size, status, search);
        long totalCount = repository.getRecordCount(status, search);
        int totalPages = (int) Math.ceil((double) totalCount / size);

        Map<String, Object> response = new HashMap<>();
        response.put("records", records);
        response.put("page", page);
        response.put("size", size);
        response.put("totalRecords", totalCount);
        response.put("totalPages", totalPages);

        return response;
    }

    @GetMapping("/api/records/export")
    public void exportRecordsCsv(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search,
            jakarta.servlet.http.HttpServletResponse response) throws IOException {

        response.setContentType("text/csv");
        response.setHeader("Content-Disposition", "attachment; filename=migration_records_export.csv");

        java.io.PrintWriter writer = response.getWriter();
        writer.println("box_file_id,box_file_name,box_file_path,user_email,status,error_message,original_format,converted_format,file_size_bytes,google_drive_file_id,google_drive_web_view_link,created_at,completed_at");

        int page = 0;
        int pageSize = 500;
        List<MigrationRecord> chunk;

        do {
            chunk = repository.getRecordsPaginated(page * pageSize, pageSize, status, search);
            for (MigrationRecord record : chunk) {
                writer.print(escapeCsv(record.getBoxFileId())); writer.print(',');
                writer.print(escapeCsv(record.getBoxFileName())); writer.print(',');
                writer.print(escapeCsv(record.getBoxFilePath())); writer.print(',');
                writer.print(escapeCsv(record.getUserEmail())); writer.print(',');
                writer.print(escapeCsv(record.getStatus() != null ? record.getStatus().name() : "")); writer.print(',');
                writer.print(escapeCsv(record.getErrorMessage())); writer.print(',');
                writer.print(escapeCsv(record.getOriginalFormat())); writer.print(',');
                writer.print(escapeCsv(record.getConvertedFormat())); writer.print(',');
                writer.print(record.getFileSizeBytes() != null ? record.getFileSizeBytes() : ""); writer.print(',');
                writer.print(escapeCsv(record.getGoogleDriveFileId())); writer.print(',');
                writer.print(escapeCsv(record.getGoogleDriveWebViewLink())); writer.print(',');
                writer.print(record.getCreatedAt() != null ? record.getCreatedAt().toString() : ""); writer.print(',');
                writer.println(record.getCompletedAt() != null ? record.getCompletedAt().toString() : "");
            }
            writer.flush();
            page++;
        } while (chunk.size() == pageSize);
    }

    private String escapeCsv(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    @PostMapping("/api/database/reset")
    public Map<String, Object> resetDatabase() {
        if (migrationRunning.get() || orchestrator.isRunning()) {
            logger.warn("Database reset requested but migration is currently running");
            return Map.of(
                "success", false,
                "message", "Cannot reset database while migration is running. Please stop the migration first."
            );
        }

        try {
            logger.info("Resetting database - clearing all migration records");

            // Get current record count before reset
            Map<MigrationStatus, Long> stats = repository.getStatistics();
            long totalRecords = stats.values().stream().mapToLong(Long::longValue).sum();

            // Delete all records from the database
            repository.deleteAllRecords();

            logger.info("Database reset complete. Deleted {} records", totalRecords);

            return Map.of(
                "success", true,
                "message", "Database reset successfully",
                "deletedRecords", totalRecords
            );

        } catch (Exception e) {
            logger.error("Failed to reset database", e);
            return Map.of(
                "success", false,
                "message", "Failed to reset database: " + e.getMessage()
            );
        }
    }
}
