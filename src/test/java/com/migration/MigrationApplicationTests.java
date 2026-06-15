package com.migration;

import com.box.sdk.BoxAPIConnection;
import com.migration.config.CredentialsManager;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:sqlite::memory:",
    "spring.datasource.driver-class-name=org.sqlite.JDBC",
    "spring.batch.job.enabled=false"
})
@ActiveProfiles("test")
class MigrationApplicationTests {

    static {
        System.setProperty("net.bytebuddy.experimental", "true");
    }

    @MockBean
    private CredentialsManager credentialsManager;

    @MockBean
    private BoxAPIConnection boxAPIConnection;

    @Test
    void contextLoads() {
        // Verifies that the Spring application context boots up successfully.
    }
}
