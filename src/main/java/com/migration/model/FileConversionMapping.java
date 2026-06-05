package com.migration.model;

import java.util.HashMap;
import java.util.Map;

public class FileConversionMapping {
    private static final Map<String, String> MIME_TYPE_MAP = new HashMap<>();
    private static final Map<String, String> DECOUPLED_MIME_MAP = new HashMap<>();
    private static final Map<String, String> DECOUPLED_EXTENSION_MAP = new HashMap<>();

    static {
        MIME_TYPE_MAP.put("application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                         "application/vnd.google-apps.document");
        MIME_TYPE_MAP.put("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                         "application/vnd.google-apps.spreadsheet");
        MIME_TYPE_MAP.put("application/vnd.openxmlformats-officedocument.presentationml.presentation",
                         "application/vnd.google-apps.presentation");

        DECOUPLED_MIME_MAP.put("application/vnd.google-apps.document",
                "application/vnd.google-apps.document.internal");
        DECOUPLED_MIME_MAP.put("application/vnd.google-apps.spreadsheet",
                "application/vnd.google-apps.spreadsheet.internal");
        DECOUPLED_MIME_MAP.put("application/vnd.google-apps.presentation",
                "application/vnd.google-apps.presentation.internal");

        DECOUPLED_EXTENSION_MAP.put("application/vnd.google-apps.document", "gdoc");
        DECOUPLED_EXTENSION_MAP.put("application/vnd.google-apps.spreadsheet", "gsheet");
        DECOUPLED_EXTENSION_MAP.put("application/vnd.google-apps.presentation", "gslides");
    }

    public static String getGoogleMimeType(String officeMimeType) {
        return MIME_TYPE_MAP.get(officeMimeType);
    }

    public static String getDecoupledMimeType(String googleMimeType) {
        return DECOUPLED_MIME_MAP.get(googleMimeType);
    }

    public static String getDecoupledExtension(String googleMimeType) {
        return DECOUPLED_EXTENSION_MAP.get(googleMimeType);
    }

    public static String getDecoupledFileName(String originalFileName, String officeMimeType) {
        String googleMimeType = getGoogleMimeType(officeMimeType);
        String extension = getDecoupledExtension(googleMimeType);
        if (extension == null) {
            return originalFileName;
        }

        String baseName = originalFileName;
        int dotIndex = originalFileName.lastIndexOf('.');
        if (dotIndex > 0) {
            baseName = originalFileName.substring(0, dotIndex);
        }
        return baseName + "." + extension;
    }

    public static boolean isConvertible(String mimeType) {
        return MIME_TYPE_MAP.containsKey(mimeType);
    }

    public static Map<String, String> getAllMappings() {
        return new HashMap<>(MIME_TYPE_MAP);
    }
}
