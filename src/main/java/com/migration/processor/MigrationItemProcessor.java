package com.migration.processor;

import com.box.sdk.BoxFile;
import com.migration.model.ConversionResult;
import com.migration.model.FileConversionMapping;
import com.migration.model.MigrationRecord;
import com.migration.model.MigrationStatus;
import com.migration.repository.MigrationRepository;
import com.migration.service.BoxService;
import com.migration.service.ConversionService;
import com.migration.service.GoogleDriveService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.sql.Timestamp;

@Component
public class MigrationItemProcessor implements ItemProcessor<MigrationRecord, MigrationRecord> {
    private static final Logger logger = LoggerFactory.getLogger(MigrationItemProcessor.class);

    private final BoxService boxService;
    private final GoogleDriveService driveService;
    private final ConversionService conversionService;
    private final MigrationRepository repository;

    public MigrationItemProcessor(BoxService boxService, GoogleDriveService driveService,
                                  ConversionService conversionService, MigrationRepository repository) {
        this.boxService = boxService;
        this.driveService = driveService;
        this.conversionService = conversionService;
        this.repository = repository;
    }

    @Override
    public MigrationRecord process(MigrationRecord record) throws Exception {
        logger.info("Starting batch migration for Box file: {} ({})", record.getBoxFileId(), record.getBoxFileName());

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

            String mimeType = getMimeTypeFromFileName(record.getBoxFileName());
            String decoupledFileName = FileConversionMapping.getDecoupledFileName(record.getBoxFileName(), mimeType);

            if (decoupledFileName.equals(record.getBoxFileName())) {
                throw new FileAlreadyExistsException(
                        "File already appears to be in decoupled format: " + record.getBoxFileName());
            }

            String driveFolderId = driveService.ensureFolderPath(record.getBoxFilePath(), record.getUserEmail());

            repository.updateStatus(record.getBoxFileId(), MigrationStatus.CONVERTING, null);
            ConversionResult result = conversionService.uploadAndConvert(
                    fileContent,
                    record.getBoxFileName(),
                    mimeType,
                    driveFolderId,
                    record.getUserEmail()
            );

            record.setGoogleDriveFileId(result.getFileId());
            record.setGoogleDrivePath(record.getBoxFilePath());
            record.setGoogleDriveWebViewLink(result.getWebViewLink());

            try {
                repository.updateStatus(record.getBoxFileId(), MigrationStatus.EXPORTING, null);
                byte[] decoupledContent = driveService.exportDecoupledDocument(
                        result.getFileId(),
                        result.getMimeType(),
                        record.getUserEmail()
                );

                repository.updateStatus(record.getBoxFileId(), MigrationStatus.UPLOADING, null);
                BoxFile.Info updatedFile = boxService.uploadNewVersionWithName(
                        record.getBoxFileId(),
                        new ByteArrayInputStream(decoupledContent),
                        decoupledFileName
                );

                record.setBoxFileName(updatedFile.getName());
                record.setFileSizeBytes(updatedFile.getSize());

                driveService.deleteFile(result.getFileId(), record.getUserEmail());
                record.setGoogleDriveFileId(null);
            } catch (Exception exportError) {
                logger.warn("Decoupled export/upload failed; temporary Google file retained: {}",
                        result.getFileId(), exportError);
                throw exportError;
            }

            record.setConvertedFormat(getDecoupledFormatLabel(result.getMimeType()));
            record.setStatus(MigrationStatus.COMPLETED);
            record.setCompletedAt(new Timestamp(System.currentTimeMillis()));

            repository.insertOrUpdateRecord(record);

            logger.info("Migration completed successfully for Box file: {} -> new version as '{}'",
                       record.getBoxFileId(), record.getBoxFileName());

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

    private String getDecoupledFormatLabel(String googleMimeType) {
        if (googleMimeType == null) {
            return "unknown";
        }

        return switch (googleMimeType) {
            case "application/vnd.google-apps.document" -> "Word → Google Docs decoupled";
            case "application/vnd.google-apps.spreadsheet" -> "Excel → Google Sheets decoupled";
            case "application/vnd.google-apps.presentation" -> "PowerPoint → Google Slides decoupled";
            default -> googleMimeType;
        };
    }

    public static class FileAlreadyExistsException extends Exception {
        public FileAlreadyExistsException(String message) {
            super(message);
        }
    }
}
