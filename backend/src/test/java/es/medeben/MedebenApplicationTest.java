package es.medeben;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Full context integration test against a real PostgreSQL 16 via
 * Testcontainers (no H2 — same engine as production).
 *
 * <p>Requires Docker. When Docker is not available the test is skipped
 * ({@code disabledWithoutDocker = true}) so {@code mvn test} stays green
 * on machines without it. See backend/README.md.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class MedebenApplicationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Test
    @DisplayName("Spring context starts against real PostgreSQL")
    void contextLoads() {
        // Boots the full application context (JPA, Flyway, Security) against
        // a throwaway PostgreSQL container. Failing to start fails the test.
    }
}
