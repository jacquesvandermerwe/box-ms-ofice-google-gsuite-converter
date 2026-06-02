package com.migration.config;

import com.box.sdk.BoxAPIConnection;
import com.box.sdk.BoxDeveloperEditionAPIConnection;
import com.box.sdk.BoxConfig;
import com.box.sdk.IAccessTokenCache;
import com.box.sdk.InMemoryLRUAccessTokenCache;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.util.Collections;

public class CredentialsManager {
    private static final Logger logger = LoggerFactory.getLogger(CredentialsManager.class);
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();

    private final AppConfig config;
    private BoxAPIConnection boxConnection;
    private ServiceAccountCredentials googleCredentials;

    public CredentialsManager(AppConfig config) {
        this.config = config;
    }

    public BoxAPIConnection getBoxConnection() {
        if (boxConnection == null) {
            boxConnection = createBoxConnection();
        }
        return boxConnection;
    }

    private BoxAPIConnection createBoxConnection() {
        logger.info("Initializing Box API connection");

        String developerToken = config.getBoxDeveloperToken();

        if (developerToken != null && !developerToken.isEmpty() &&
            !developerToken.equals("YOUR_BOX_DEV_TOKEN")) {
            logger.info("Using Box Developer Token authentication");
            return new BoxAPIConnection(developerToken);
        }

        logger.warn("Box credentials not configured properly. Please set box.developer.token in application.properties");
        throw new IllegalStateException("Box authentication not configured. Please provide a developer token.");
    }

    public Drive getGoogleDriveService() throws IOException, GeneralSecurityException {
        return getGoogleDriveServiceForUser(null);
    }

    public Drive getGoogleDriveServiceForUser(String userEmail) throws IOException, GeneralSecurityException {
        logger.info("Initializing Google Drive API service" +
                   (userEmail != null ? " for user: " + userEmail : ""));

        if (googleCredentials == null) {
            googleCredentials = loadGoogleCredentials();
        }

        GoogleCredentials credentials;
        if (userEmail != null && !userEmail.isEmpty()) {
            credentials = googleCredentials.createDelegated(userEmail);
        } else {
            credentials = googleCredentials;
        }

        final NetHttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();

        return new Drive.Builder(httpTransport, JSON_FACTORY, new HttpCredentialsAdapter(credentials))
                .setApplicationName(config.getGoogleApplicationName())
                .build();
    }

    private ServiceAccountCredentials loadGoogleCredentials() throws IOException {
        String credentialsPath = config.getGoogleCredentialsFile();

        if (credentialsPath == null || credentialsPath.isEmpty() ||
            credentialsPath.equals("path/to/credentials.json")) {
            throw new IllegalStateException(
                "Google credentials file not configured. Please set google.credentials.file in application.properties");
        }

        logger.info("Loading Google service account credentials from: {}", credentialsPath);

        try (FileInputStream serviceAccountStream = new FileInputStream(credentialsPath)) {
            GoogleCredentials credentials = GoogleCredentials.fromStream(serviceAccountStream)
                    .createScoped(Collections.singletonList(DriveScopes.DRIVE));

            if (!(credentials instanceof ServiceAccountCredentials)) {
                throw new IllegalStateException("Credentials file must be a service account key");
            }

            logger.info("Google service account credentials loaded successfully");
            return (ServiceAccountCredentials) credentials;
        }
    }
}
