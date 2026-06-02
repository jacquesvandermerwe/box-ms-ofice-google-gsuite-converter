package com.migration.model;

import java.sql.Timestamp;

public class MigrationRecord {
    private Long id;
    private String boxFileId;
    private String boxFilePath;
    private String boxFileName;
    private String userEmail;
    private String googleDriveFileId;
    private String googleDrivePath;
    private String googleDriveWebViewLink;
    private MigrationStatus status;
    private String errorMessage;
    private String originalFormat;
    private String convertedFormat;
    private Long fileSizeBytes;
    private Timestamp createdAt;
    private Timestamp updatedAt;
    private Timestamp completedAt;

    public MigrationRecord() {
    }

    public MigrationRecord(String boxFileId, String boxFilePath, String boxFileName, String userEmail) {
        this.boxFileId = boxFileId;
        this.boxFilePath = boxFilePath;
        this.boxFileName = boxFileName;
        this.userEmail = userEmail;
        this.status = MigrationStatus.PENDING;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getBoxFileId() {
        return boxFileId;
    }

    public void setBoxFileId(String boxFileId) {
        this.boxFileId = boxFileId;
    }

    public String getBoxFilePath() {
        return boxFilePath;
    }

    public void setBoxFilePath(String boxFilePath) {
        this.boxFilePath = boxFilePath;
    }

    public String getBoxFileName() {
        return boxFileName;
    }

    public void setBoxFileName(String boxFileName) {
        this.boxFileName = boxFileName;
    }

    public String getUserEmail() {
        return userEmail;
    }

    public void setUserEmail(String userEmail) {
        this.userEmail = userEmail;
    }

    public String getGoogleDriveFileId() {
        return googleDriveFileId;
    }

    public void setGoogleDriveFileId(String googleDriveFileId) {
        this.googleDriveFileId = googleDriveFileId;
    }

    public String getGoogleDrivePath() {
        return googleDrivePath;
    }

    public void setGoogleDrivePath(String googleDrivePath) {
        this.googleDrivePath = googleDrivePath;
    }

    public String getGoogleDriveWebViewLink() {
        return googleDriveWebViewLink;
    }

    public void setGoogleDriveWebViewLink(String googleDriveWebViewLink) {
        this.googleDriveWebViewLink = googleDriveWebViewLink;
    }

    public MigrationStatus getStatus() {
        return status;
    }

    public void setStatus(MigrationStatus status) {
        this.status = status;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getOriginalFormat() {
        return originalFormat;
    }

    public void setOriginalFormat(String originalFormat) {
        this.originalFormat = originalFormat;
    }

    public String getConvertedFormat() {
        return convertedFormat;
    }

    public void setConvertedFormat(String convertedFormat) {
        this.convertedFormat = convertedFormat;
    }

    public Long getFileSizeBytes() {
        return fileSizeBytes;
    }

    public void setFileSizeBytes(Long fileSizeBytes) {
        this.fileSizeBytes = fileSizeBytes;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }

    public Timestamp getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Timestamp updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Timestamp getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Timestamp completedAt) {
        this.completedAt = completedAt;
    }

    @Override
    public String toString() {
        return "MigrationRecord{" +
                "boxFileId='" + boxFileId + '\'' +
                ", boxFilePath='" + boxFilePath + '\'' +
                ", boxFileName='" + boxFileName + '\'' +
                ", userEmail='" + userEmail + '\'' +
                ", status=" + status +
                ", googleDriveFileId='" + googleDriveFileId + '\'' +
                '}';
    }
}
