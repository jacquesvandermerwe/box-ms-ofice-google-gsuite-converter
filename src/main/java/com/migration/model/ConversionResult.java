package com.migration.model;

public class ConversionResult {
    private String fileId;
    private String webViewLink;
    private String mimeType;
    private Long size;

    public ConversionResult() {
    }

    public ConversionResult(String fileId, String webViewLink, String mimeType, Long size) {
        this.fileId = fileId;
        this.webViewLink = webViewLink;
        this.mimeType = mimeType;
        this.size = size;
    }

    public String getFileId() {
        return fileId;
    }

    public void setFileId(String fileId) {
        this.fileId = fileId;
    }

    public String getWebViewLink() {
        return webViewLink;
    }

    public void setWebViewLink(String webViewLink) {
        this.webViewLink = webViewLink;
    }

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public Long getSize() {
        return size;
    }

    public void setSize(Long size) {
        this.size = size;
    }

    @Override
    public String toString() {
        return "ConversionResult{" +
                "fileId='" + fileId + '\'' +
                ", webViewLink='" + webViewLink + '\'' +
                ", mimeType='" + mimeType + '\'' +
                '}';
    }
}
