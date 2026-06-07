package com.migration.config;

import com.box.sdk.BoxAPIConnection;
import com.box.sdk.BoxConfig;
import com.box.sdk.BoxDeveloperEditionAPIConnection;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.util.Collections;

import org.springframework.stereotype.Component;

@Component
public class CredentialsManager {
    private static final Logger logger = LoggerFactory.getLogger(CredentialsManager.class);
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    private static final String TOKENS_DIRECTORY_PATH = "tokens";

    private final AppConfig config;
    private BoxAPIConnection boxConnection;
    private ServiceAccountCredentials serviceAccountCredentials;
    private Credential oauthCredential;
    private final NetHttpTransport httpTransport;
    private Drive cachedOAuthDriveService; // Cache for OAuth mode (single user)

    public CredentialsManager(AppConfig config) throws GeneralSecurityException, IOException {
        this.config = config;
        this.httpTransport = GoogleNetHttpTransport.newTrustedTransport();

        // Initialize Google credentials based on auth type
        if (config.isOAuthMode()) {
            logger.info("Using OAuth authentication mode for Google Drive");
            this.oauthCredential = getOAuthCredential();
        } else {
            logger.info("Using Service Account authentication mode for Google Drive");
            this.serviceAccountCredentials = loadServiceAccountCredentials();
        }
    }

    public BoxAPIConnection getBoxConnection() {
        if (boxConnection == null) {
            boxConnection = createBoxConnection();
        }
        return boxConnection;
    }

    private BoxAPIConnection createBoxConnection() {
        logger.info("Initializing Box API connection");

        // Option 1: Try JWT config file first (production, auto-refreshing)
        String configFile = config.getBoxConfigFile();
        if (configFile != null && !configFile.isEmpty()) {
            try {
                logger.info("Using Box JWT authentication from config file: {}", configFile);
                Reader reader = new FileReader(configFile);
                BoxConfig boxConfig = BoxConfig.readFrom(reader);
                BoxDeveloperEditionAPIConnection api = BoxDeveloperEditionAPIConnection.getAppEnterpriseConnection(boxConfig);
                logger.info("Box JWT authentication successful");
                return api;
            } catch (IOException e) {
                logger.error("Failed to load Box JWT config from file: {}", configFile, e);
                throw new IllegalStateException("Failed to load Box JWT config file", e);
            }
        }

        // Option 2: Fall back to developer token (testing, expires in 60 minutes)
        String developerToken = config.getBoxDeveloperToken();
        if (developerToken != null && !developerToken.isEmpty() &&
            !developerToken.equals("YOUR_BOX_DEV_TOKEN")) {
            logger.info("Using Box Developer Token authentication");
            logger.warn("Developer tokens expire after 60 minutes. For production, use box.config.file with JWT authentication.");
            return new BoxAPIConnection(developerToken);
        }

        logger.error("Box credentials not configured properly.");
        logger.error("Set either:");
        logger.error("  - box.config.file=/path/to/box_config.json (recommended for production)");
        logger.error("  - box.developer.token=YOUR_TOKEN (for quick testing, expires in 60 min)");
        throw new IllegalStateException("Box authentication not configured. Please provide either box.config.file or box.developer.token.");
    }

    public Drive getGoogleDriveService() throws IOException, GeneralSecurityException {
        return getGoogleDriveServiceForUser(null);
    }

    public Drive getGoogleDriveServiceForUser(String userEmail) throws IOException, GeneralSecurityException {
        if (config.isOAuthMode()) {
            return getOAuthDriveService(userEmail);
        } else {
            return getServiceAccountDriveService(userEmail);
        }
    }

    /**
     * OAuth mode - for personal Google accounts
     * User email is ignored; all files go to the authenticated user's Drive
     * Drive service is cached since OAuth mode only uses one user
     */
    private Drive getOAuthDriveService(String userEmail) throws IOException {
        if (userEmail != null && !userEmail.isEmpty()) {
            logger.debug("OAuth mode: Ignoring user_email '{}' - uploading to authenticated user's Drive", userEmail);
        }

        // Cache Drive service in OAuth mode since we only have one user
        if (cachedOAuthDriveService == null) {
            logger.info("Creating Google Drive service using OAuth credentials");
            cachedOAuthDriveService = new Drive.Builder(httpTransport, JSON_FACTORY, oauthCredential)
                    .setApplicationName(config.getGoogleApplicationName())
                    .build();
        } else {
            logger.debug("Reusing cached OAuth Drive service");
        }

        return cachedOAuthDriveService;
    }

    /**
     * Service Account mode - for Google Workspace with domain-wide delegation
     * Impersonates the specified user email
     */
    private Drive getServiceAccountDriveService(String userEmail) throws IOException, GeneralSecurityException {
        String impersonatedUser = resolveImpersonatedUser(userEmail);
        logger.info("Creating Google Drive service impersonating: {}", impersonatedUser);

        GoogleCredentials credentials = serviceAccountCredentials.createDelegated(impersonatedUser);

        return new Drive.Builder(httpTransport, JSON_FACTORY, new HttpCredentialsAdapter(credentials))
                .setApplicationName(config.getGoogleApplicationName())
                .build();
    }

    public String resolveImpersonatedUser(String userEmail) {
        String email = userEmail != null ? userEmail.trim() : "";
        if (email.isEmpty() || isIgnoredUserEmailPlaceholder(email)) {
            String defaultUser = config.getGoogleImpersonateUser();
            if (defaultUser != null && !defaultUser.trim().isEmpty()) {
                email = defaultUser.trim();
                logger.debug("Using google.impersonate.user default: {}", email);
            }
        }

        if (email.isEmpty() || isIgnoredUserEmailPlaceholder(email)) {
            throw new IllegalStateException(
                "Service account mode requires a Google Workspace user to impersonate. "
                    + "Replace 'ignored' in CSV user_email with a real @yourdomain.com address, "
                    + "or set google.impersonate.user in application.properties.");
        }

        if (!email.contains("@") || email.startsWith("@") || email.endsWith("@")) {
            throw new IllegalStateException(
                "Invalid user_email for service account impersonation: '" + email + "'. "
                    + "Use a full Google Workspace address (e.g. user@company.com).");
        }

        return email;
    }

    private static boolean isIgnoredUserEmailPlaceholder(String value) {
        return "ignored".equalsIgnoreCase(value)
                || "n/a".equalsIgnoreCase(value)
                || "na".equalsIgnoreCase(value)
                || "-".equals(value);
    }

    /**
     * Load OAuth credentials for personal Google accounts
     * Opens browser for user consent on first run
     */
    private Credential getOAuthCredential() throws IOException {
        String credentialsPath = config.getGoogleCredentialsFile();

        if (credentialsPath == null || credentialsPath.isEmpty() ||
            credentialsPath.equals("path/to/credentials.json")) {
            throw new IllegalStateException(
                "Google credentials file not configured. Please set google.credentials.file in application.properties");
        }

        logger.info("Loading OAuth client credentials from: {}", credentialsPath);

        if (isServiceAccountKeyFile(credentialsPath)) {
            throw new IllegalStateException(
                "Credentials file is a service account key, but google.auth.type=oauth. "
                    + "Set google.auth.type=service_account, or point google.credentials.file "
                    + "at an OAuth 2.0 Desktop client JSON (with an 'installed' section).");
        }

        // Load client secrets
        GoogleClientSecrets clientSecrets;
        try (FileInputStream in = new FileInputStream(credentialsPath)) {
            clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));
        }

        if (clientSecrets.getDetails() == null || clientSecrets.getDetails().getClientId() == null) {
            throw new IllegalStateException(
                "Credentials file is not a valid OAuth client JSON. "
                    + "Download an OAuth 2.0 Desktop client from Google Cloud Console → Credentials.");
        }

        // Build flow and trigger user authorization
        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                httpTransport, JSON_FACTORY, clientSecrets, Collections.singleton(DriveScopes.DRIVE))
                .setDataStoreFactory(new FileDataStoreFactory(new File(TOKENS_DIRECTORY_PATH)))
                .setAccessType("offline")
                .build();

        LocalServerReceiver receiver = new LocalServerReceiver.Builder()
                .setPort(8080)
                .build();

        Credential credential = new AuthorizationCodeInstalledApp(flow, receiver).authorize("user");

        logger.info("OAuth credentials obtained successfully");
        logger.info("Authenticated user will be used for all file uploads");

        return credential;
    }

    /**
     * Load service account credentials for Google Workspace
     */
    private ServiceAccountCredentials loadServiceAccountCredentials() throws IOException {
        String credentialsPath = config.getGoogleCredentialsFile();

        if (credentialsPath == null || credentialsPath.isEmpty() ||
            credentialsPath.equals("path/to/credentials.json")) {
            throw new IllegalStateException(
                "Google credentials file not configured. Please set google.credentials.file in application.properties");
        }

        logger.info("Loading Google service account credentials from: {}", credentialsPath);

        if (!isServiceAccountKeyFile(credentialsPath)) {
            throw new IllegalStateException(
                "Credentials file is not a service account key, but google.auth.type=service_account. "
                    + "Set google.auth.type=oauth for a Desktop OAuth client JSON, or use a "
                    + "service account key JSON (with \"type\": \"service_account\").");
        }

        try (FileInputStream serviceAccountStream = new FileInputStream(credentialsPath)) {
            GoogleCredentials credentials = GoogleCredentials.fromStream(serviceAccountStream)
                    .createScoped(Collections.singleton(DriveScopes.DRIVE));

            if (!(credentials instanceof ServiceAccountCredentials)) {
                throw new IllegalStateException(
                    "Credentials file must be a service account key. Found: " + credentials.getClass().getSimpleName() +
                    "\nFor personal accounts, set google.auth.type=oauth in application.properties");
            }

            logger.info("Google service account credentials loaded successfully");
            logger.info("Domain-wide delegation enabled - can impersonate users");
            return (ServiceAccountCredentials) credentials;
        }
    }

    public boolean isOAuthMode() {
        return config.isOAuthMode();
    }

    public boolean isServiceAccountMode() {
        return config.isServiceAccountMode();
    }

    /**
     * Returns a valid Google OAuth access token (refreshes if expired).
     * OAuth mode only — uses the client JSON + stored refresh token in tokens/.
     */
    private boolean isServiceAccountKeyFile(String credentialsPath) throws IOException {
        String content = Files.readString(Paths.get(credentialsPath));
        return content.contains("\"type\"") && content.contains("service_account");
    }

    public String getGoogleAccessToken() throws IOException {
        if (!config.isOAuthMode()) {
            throw new IllegalStateException(
                "getGoogleAccessToken() requires google.auth.type=oauth");
        }
        Long expiresIn = oauthCredential.getExpiresInSeconds();
        if (expiresIn == null || expiresIn <= 60) {
            oauthCredential.refreshToken();
        }
        return oauthCredential.getAccessToken();
    }
}
