package com.migration;

import com.migration.config.AppConfig;
import com.migration.config.CredentialsManager;

/**
 * Prints a Google OAuth access token for curl / test-export-gdoc.sh.
 * First run opens a browser for consent; later runs reuse tokens/ refresh token.
 */
public class GoogleTokenCli {

    public static void main(String[] args) throws Exception {
        AppConfig config = new AppConfig("application.properties");
        if (!config.isOAuthMode()) {
            System.err.println("Set google.auth.type=oauth in application.properties");
            System.exit(1);
        }

        CredentialsManager credentials = new CredentialsManager(config);
        System.out.println(credentials.getGoogleAccessToken());
    }
}
