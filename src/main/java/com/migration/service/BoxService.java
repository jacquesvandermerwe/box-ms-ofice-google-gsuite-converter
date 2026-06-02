package com.migration.service;

import com.box.sdk.BoxAPIConnection;
import com.box.sdk.BoxAPIException;
import com.box.sdk.BoxFile;
import com.box.sdk.BoxFolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class BoxService {
    private static final Logger logger = LoggerFactory.getLogger(BoxService.class);
    private final BoxAPIConnection api;
    private final String asUserId;

    public BoxService(BoxAPIConnection api) {
        this(api, null);
    }

    public BoxService(BoxAPIConnection api, String asUserId) {
        this.api = api;
        this.asUserId = asUserId;
        if (asUserId != null && !asUserId.isEmpty()) {
            logger.info("Box service configured to act as user: {}", asUserId);
            api.asUser(asUserId);
        }
    }

    public InputStream downloadFile(String boxFileId) {
        logger.info("Downloading file from Box: {}", boxFileId);
        try {
            BoxFile file = new BoxFile(api, boxFileId);
            java.io.ByteArrayOutputStream outputStream = new java.io.ByteArrayOutputStream();
            file.download(outputStream);
            return new java.io.ByteArrayInputStream(outputStream.toByteArray());
        } catch (BoxAPIException e) {
            logger.error("Failed to download file from Box: {}", boxFileId, e);
            throw new RuntimeException("Failed to download file from Box: " + boxFileId, e);
        }
    }

    public BoxFile.Info getFileInfo(String boxFileId) {
        logger.info("Retrieving file info from Box: {}", boxFileId);
        if (asUserId != null) {
            logger.info("Acting as Box user: {}", asUserId);
        } else {
            logger.warn("No As-User header set - service account acting as itself (may not have file access)");
        }
        try {
            BoxFile file = new BoxFile(api, boxFileId);
            BoxFile.Info info = file.getInfo("name", "size", "path_collection", "parent", "content_created_at", "content_modified_at");
            logger.info("File info retrieved successfully: {} (size: {} bytes)", info.getName(), info.getSize());
            return info;
        } catch (BoxAPIException e) {
            logger.error("Failed to retrieve file info from Box: {}", boxFileId, e);
            logger.error("Box API Response Code: {}", e.getResponseCode());
            logger.error("Box API Error: {}", e.getMessage());
            throw new RuntimeException("Failed to retrieve file info from Box: " + boxFileId, e);
        }
    }

    public String getFilePath(BoxFile.Info fileInfo) {
        logger.debug("Building file path for: {}", fileInfo.getName());
        List<String> pathComponents = new ArrayList<>();

        if (fileInfo.getPathCollection() != null) {
            for (BoxFolder.Info folderInfo : fileInfo.getPathCollection()) {
                if (!folderInfo.getID().equals("0")) {
                    pathComponents.add(folderInfo.getName());
                }
            }
        }

        if (fileInfo.getParent() != null && !fileInfo.getParent().getID().equals("0")) {
            String parentName = fileInfo.getParent().getName();
            if (parentName != null && !pathComponents.contains(parentName)) {
                pathComponents.add(parentName);
            }
        }

        StringBuilder path = new StringBuilder();
        for (String component : pathComponents) {
            path.append("/").append(component);
        }

        if (path.length() == 0) {
            path.append("/");
        }

        String fullPath = path.toString();
        logger.debug("File path: {}", fullPath);
        return fullPath;
    }

    public boolean fileExists(String boxFileId) {
        logger.debug("Checking if file exists in Box: {}", boxFileId);
        try {
            BoxFile file = new BoxFile(api, boxFileId);
            file.getInfo("name");
            return true;
        } catch (BoxAPIException e) {
            if (e.getResponseCode() == 404) {
                logger.warn("File not found in Box: {}", boxFileId);
                return false;
            }
            logger.error("Error checking file existence in Box: {}", boxFileId, e);
            throw new RuntimeException("Error checking file existence: " + boxFileId, e);
        }
    }
}
