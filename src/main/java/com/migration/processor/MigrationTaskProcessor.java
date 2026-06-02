package com.migration.processor;

import com.box.sdk.BoxFile;
import com.migration.model.ConversionResult;
import com.migration.model.MigrationRecord;
import com.migration.model.MigrationStatus;
import com.migration.repository.MigrationRepository;
import com.migration.service.BoxService;
import com.migration.service.ConversionService;
import com.migration.service.GoogleDriveService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.sql.Timestamp;
import java.util.concurrent.Callable;

public class MigrationTaskProcessor implements Callable<MigrationRecord> {
    private static final Logger logger = LoggerFactory.getLogger(MigrationTaskProcessor.class);

    private final MigrationRecord record;
    private final BoxService boxService;
    private final GoogleDriveService driveService;
    private final ConversionService conversionService;
    private final MigrationRepository repository;

    public MigrationTaskProcessor(MigrationRecord record, BoxService boxService,
                                  GoogleDriveService driveService, ConversionService conversionService,
                                  MigrationRepository repository) {
        this.record = record;
        this.boxService = boxService;
        this.driveService = driveService;
        this.conversionService = conversionService;
        this.repository = repository;
    }

    @Override
    public MigrationRecord call() {
        logger.info("Starting migration for Box file: {} ({})", record.getBoxFileId(), record.getBoxFileName());

        try {
            repository.updateStatus(record.getBoxFileId(), MigrationStatus.IN_PROGRESS, null);

            repository.updateStatus(record.getBoxFileId(), MigrationStatus.DOWNLOADING, null);
            BoxFile.Info fileInfo = boxService.getFileInfo(record.getBoxFileId());

            String filePath = boxService.getFilePath(fileInfo);
            record.setBoxFilePath(filePath);
            record.setBoxFileName(fileInfo.getName());
            record.setFileSizeBytes(fileInfo.getSize());
            record.setOriginalFormat(fileInfo.getName().substring(fileInfo.getName().lastIndexOf('.') + 1));

            logger.info("Downloading file from Box: {} ({})", fileInfo.getName(), fileInfo.getSize());
            InputStream fileContent = boxService.downloadFile(record.getBoxFileId());

            String folderId = driveService.ensureFolderPath(record.getBoxFilePath(), record.getUserEmail());

            // Check for existing file - use converted name (without Office extension) for duplicate detection
            String fileNameToCheck = getConvertedFileName(record.getBoxFileName());
            logger.info("Checking for duplicates: original='{}', converted='{}'", record.getBoxFileName(), fileNameToCheck);

            if (driveService.fileExistsAtPath(fileNameToCheck, folderId, record.getUserEmail())) {
                throw new FileAlreadyExistsException(
                        "File already exists at destination: " + record.getBoxFilePath() + "/" + fileNameToCheck);
            }

            repository.updateStatus(record.getBoxFileId(), MigrationStatus.UPLOADING, null);

            String mimeType = getMimeTypeFromFileName(record.getBoxFileName());

            repository.updateStatus(record.getBoxFileId(), MigrationStatus.CONVERTING, null);
            ConversionResult result = conversionService.uploadAndConvert(
                    fileContent,
                    record.getBoxFileName(),
                    mimeType,
                    folderId,
                    record.getUserEmail()
            );

            record.setGoogleDriveFileId(result.getFileId());
            record.setGoogleDrivePath(record.getBoxFilePath());
            record.setGoogleDriveWebViewLink(result.getWebViewLink());
            record.setConvertedFormat(getFormatFromMimeType(result.getMimeType()));
            record.setStatus(MigrationStatus.COMPLETED);
            record.setCompletedAt(new Timestamp(System.currentTimeMillis()));

            repository.insertOrUpdateRecord(record);

            logger.info("Migration completed successfully for Box file: {} -> Google Drive file: {}",
                       record.getBoxFileId(), record.getGoogleDriveFileId());

            return record;

        } catch (FileAlreadyExistsException e) {
            logger.error("File already exists: {}", record.getBoxFileId(), e);
            record.setStatus(MigrationStatus.FAILED);
            record.setErrorMessage("File already exists at destination: " + e.getMessage());
            repository.insertOrUpdateRecord(record);
            return record;

        } catch (Exception e) {
            logger.error("Migration failed for Box file: {}", record.getBoxFileId(), e);
            record.setStatus(MigrationStatus.FAILED);
            record.setErrorMessage(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
            repository.insertOrUpdateRecord(record);
            return record;
        }
    }

    /**
     * Get the converted file name (without Office extension) for duplicate checking.
     * Google Workspace files don't have extensions: "Report.docx" becomes "Report"
     */
    private String getConvertedFileName(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return fileName;
        }

        String extension = fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();

        // Strip extension for Office files that will be converted to Google format
        if (extension.equals("docx") || extension.equals("xlsx") || extension.equals("pptx") ||
            extension.equals("doc") || extension.equals("xls") || extension.equals("ppt")) {
            return fileName.substring(0, fileName.lastIndexOf('.'));
        }

        // Keep extension for other file types
        return fileName;
    }

    private String getMimeTypeFromFileName(String fileName) {
        String extension = fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();

        switch (extension) {
            case "docx":
                return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "xlsx":
                return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case "pptx":
                return "application/vnd.openxmlformats-officedocument.presentationml.presentation";
            default:
                return "application/octet-stream";
        }
    }

    private String getFormatFromMimeType(String mimeType) {
        if (mimeType == null) {
            return "unknown";
        }

        if (mimeType.equals("application/vnd.google-apps.document")) {
            return "Google Docs";
        } else if (mimeType.equals("application/vnd.google-apps.spreadsheet")) {
            return "Google Sheets";
        } else if (mimeType.equals("application/vnd.google-apps.presentation")) {
            return "Google Slides";
        }

        return mimeType;
    }

    public static class FileAlreadyExistsException extends Exception {
        public FileAlreadyExistsException(String message) {
            super(message);
        }
    }
}
