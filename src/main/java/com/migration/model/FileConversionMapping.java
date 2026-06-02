package com.migration.model;

import java.util.HashMap;
import java.util.Map;

public class FileConversionMapping {
    private static final Map<String, String> MIME_TYPE_MAP = new HashMap<>();

    static {
        MIME_TYPE_MAP.put("application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                         "application/vnd.google-apps.document");
        MIME_TYPE_MAP.put("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                         "application/vnd.google-apps.spreadsheet");
        MIME_TYPE_MAP.put("application/vnd.openxmlformats-officedocument.presentationml.presentation",
                         "application/vnd.google-apps.presentation");
    }

    public static String getGoogleMimeType(String officeMimeType) {
        return MIME_TYPE_MAP.get(officeMimeType);
    }

    public static boolean isConvertible(String mimeType) {
        return MIME_TYPE_MAP.containsKey(mimeType);
    }

    public static Map<String, String> getAllMappings() {
        return new HashMap<>(MIME_TYPE_MAP);
    }
}
