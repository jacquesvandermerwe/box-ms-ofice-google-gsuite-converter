package com.migration.service;

import com.google.api.client.http.InputStreamContent;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import com.migration.config.CredentialsManager;
import com.migration.model.ConversionResult;
import com.migration.model.FileConversionMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.util.Collections;

public class ConversionService {
    private static final Logger logger = LoggerFactory.getLogger(ConversionService.class);
    private final CredentialsManager credentialsManager;

    public ConversionService(CredentialsManager credentialsManager) {
        this.credentialsManager = credentialsManager;
    }

    public ConversionResult uploadAndConvert(InputStream fileContent, String fileName,
                                            String officeMimeType, String targetFolderId,
                                            String userEmail) throws IOException {
        logger.info("Starting upload and convert for file: {} ({})", fileName, officeMimeType);

        if (!FileConversionMapping.isConvertible(officeMimeType)) {
            throw new IllegalArgumentException("File type not supported for conversion: " + officeMimeType);
        }

        String googleMimeType = FileConversionMapping.getGoogleMimeType(officeMimeType);
        if (googleMimeType == null) {
            throw new IllegalArgumentException("No Google format mapping for: " + officeMimeType);
        }

        try {
            Drive driveService = credentialsManager.getGoogleDriveServiceForUser(userEmail);

            File fileMetadata = new File();
            fileMetadata.setName(fileName);
            fileMetadata.setParents(Collections.singletonList(targetFolderId));
            fileMetadata.setMimeType(googleMimeType);

            InputStreamContent mediaContent = new InputStreamContent(officeMimeType, fileContent);

            File convertedFile = driveService.files().create(fileMetadata, mediaContent)
                    .setFields("id, name, mimeType, size, webViewLink")
                    .execute();

            logger.info("File uploaded and converted successfully: {} -> {}", fileName, convertedFile.getId());

            ConversionResult result = new ConversionResult();
            result.setFileId(convertedFile.getId());
            result.setWebViewLink(convertedFile.getWebViewLink());
            result.setMimeType(convertedFile.getMimeType());
            result.setSize(convertedFile.getSize());

            return result;

        } catch (GeneralSecurityException e) {
            logger.error("Security error during upload and convert: {}", fileName, e);
            throw new IOException("Security error during file conversion", e);
        }
    }

    public boolean verifyConversion(String googleFileId, String userEmail) throws IOException {
        logger.debug("Verifying conversion for file: {}", googleFileId);

        try {
            Drive driveService = credentialsManager.getGoogleDriveServiceForUser(userEmail);

            File file = driveService.files().get(googleFileId)
                    .setFields("id, name, mimeType")
                    .execute();

            boolean isGoogleFormat = file.getMimeType() != null &&
                    file.getMimeType().startsWith("application/vnd.google-apps.");

            if (isGoogleFormat) {
                logger.info("Conversion verified successfully for file: {}", googleFileId);
            } else {
                logger.warn("File is not in Google format: {} ({})", googleFileId, file.getMimeType());
            }

            return isGoogleFormat;

        } catch (GeneralSecurityException e) {
            logger.error("Security error during conversion verification: {}", googleFileId, e);
            throw new IOException("Security error during conversion verification", e);
        } catch (IOException e) {
            logger.error("Failed to verify conversion: {}", googleFileId, e);
            return false;
        }
    }
}
