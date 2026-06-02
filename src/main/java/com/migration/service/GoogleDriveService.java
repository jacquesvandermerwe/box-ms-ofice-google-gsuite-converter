package com.migration.service;

import com.google.api.client.http.InputStreamContent;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import com.migration.config.CredentialsManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.util.*;

public class GoogleDriveService {
    private static final Logger logger = LoggerFactory.getLogger(GoogleDriveService.class);
    private static final String FOLDER_MIME_TYPE = "application/vnd.google-apps.folder";

    private final CredentialsManager credentialsManager;
    private final Map<String, String> folderCache = new HashMap<>();

    public GoogleDriveService(CredentialsManager credentialsManager) {
        this.credentialsManager = credentialsManager;
    }

    public String ensureFolderPath(String path, String userEmail) throws IOException {
        logger.info("Ensuring folder path exists: {} for user: {}", path, userEmail);

        String cacheKey = userEmail + ":" + path;
        if (folderCache.containsKey(cacheKey)) {
            logger.debug("Folder path found in cache: {}", path);
            return folderCache.get(cacheKey);
        }

        try {
            Drive driveService = credentialsManager.getGoogleDriveServiceForUser(userEmail);

            if (path == null || path.isEmpty() || path.equals("/")) {
                return "root";
            }

            path = path.startsWith("/") ? path.substring(1) : path;
            if (path.isEmpty()) {
                return "root";
            }

            String[] folders = path.split("/");
            String parentId = "root";

            for (String folderName : folders) {
                if (folderName.isEmpty()) {
                    continue;
                }

                String folderId = findFolderByName(driveService, folderName, parentId);

                if (folderId == null) {
                    folderId = createFolder(driveService, folderName, parentId);
                    logger.info("Created folder: {} with ID: {}", folderName, folderId);
                } else {
                    logger.debug("Folder already exists: {} with ID: {}", folderName, folderId);
                }

                parentId = folderId;
            }

            folderCache.put(cacheKey, parentId);
            return parentId;

        } catch (GeneralSecurityException e) {
            logger.error("Security error while ensuring folder path: {}", path, e);
            throw new IOException("Security error while creating folder structure", e);
        }
    }

    private String findFolderByName(Drive driveService, String folderName, String parentId) throws IOException {
        String query = String.format("name='%s' and mimeType='%s' and '%s' in parents and trashed=false",
                folderName.replace("'", "\\'"), FOLDER_MIME_TYPE, parentId);

        FileList result = driveService.files().list()
                .setQ(query)
                .setSpaces("drive")
                .setFields("files(id, name)")
                .setPageSize(1)
                .execute();

        List<File> files = result.getFiles();
        if (files != null && !files.isEmpty()) {
            return files.get(0).getId();
        }

        return null;
    }

    private String createFolder(Drive driveService, String folderName, String parentId) throws IOException {
        File fileMetadata = new File();
        fileMetadata.setName(folderName);
        fileMetadata.setMimeType(FOLDER_MIME_TYPE);
        fileMetadata.setParents(Collections.singletonList(parentId));

        File folder = driveService.files().create(fileMetadata)
                .setFields("id, name")
                .execute();

        return folder.getId();
    }

    public File uploadFile(InputStream content, String fileName, String folderId,
                          String mimeType, String userEmail) throws IOException {
        logger.info("Uploading file to Google Drive: {} in folder: {}", fileName, folderId);

        try {
            Drive driveService = credentialsManager.getGoogleDriveServiceForUser(userEmail);

            File fileMetadata = new File();
            fileMetadata.setName(fileName);
            fileMetadata.setParents(Collections.singletonList(folderId));

            InputStreamContent mediaContent = new InputStreamContent(mimeType, content);

            File uploadedFile = driveService.files().create(fileMetadata, mediaContent)
                    .setFields("id, name, mimeType, size, webViewLink")
                    .execute();

            logger.info("File uploaded successfully: {} with ID: {}", fileName, uploadedFile.getId());
            return uploadedFile;

        } catch (GeneralSecurityException e) {
            logger.error("Security error while uploading file: {}", fileName, e);
            throw new IOException("Security error while uploading file", e);
        }
    }

    public File convertToGoogleFormat(Drive driveService, String fileId, String targetMimeType) throws IOException {
        logger.info("Converting file to Google format: {} -> {}", fileId, targetMimeType);

        File fileMetadata = new File();
        fileMetadata.setMimeType(targetMimeType);

        File convertedFile = driveService.files().copy(fileId, fileMetadata)
                .setFields("id, name, mimeType, webViewLink")
                .execute();

        logger.info("File converted successfully: {} -> {}", fileId, convertedFile.getId());

        try {
            driveService.files().delete(fileId).execute();
            logger.debug("Original Office file deleted: {}", fileId);
        } catch (IOException e) {
            logger.warn("Failed to delete original file after conversion: {}", fileId, e);
        }

        return convertedFile;
    }

    public boolean fileExistsAtPath(String fileName, String folderId, String userEmail) throws IOException {
        logger.debug("Checking if file exists: {} in folder: {}", fileName, folderId);

        try {
            Drive driveService = credentialsManager.getGoogleDriveServiceForUser(userEmail);

            String query = String.format("name='%s' and '%s' in parents and trashed=false",
                    fileName.replace("'", "\\'"), folderId);

            FileList result = driveService.files().list()
                    .setQ(query)
                    .setSpaces("drive")
                    .setFields("files(id, name)")
                    .setPageSize(1)
                    .execute();

            List<File> files = result.getFiles();
            boolean exists = files != null && !files.isEmpty();

            if (exists) {
                logger.info("File already exists at destination: {}", fileName);
            }

            return exists;

        } catch (GeneralSecurityException e) {
            logger.error("Security error while checking file existence: {}", fileName, e);
            throw new IOException("Security error while checking file existence", e);
        }
    }

    public boolean isOAuthMode() {
        return credentialsManager.isOAuthMode();
    }
}
