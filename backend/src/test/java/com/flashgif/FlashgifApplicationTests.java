package com.flashgif;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;

/**
 * Sanity test that doesn't require running infra (Postgres/ES/Redis/RabbitMQ).
 * A real @SpringBootTest will land once we add Testcontainers wiring.
 */
class FlashgifApplicationTests {

    @Test
    void mainClassLoads() {
        // Asserts the @SpringBootApplication class is on the classpath and
        // resolvable. Avoids a full ApplicationContext start in unit test scope.
        SpringApplication app = new SpringApplication(FlashgifApplication.class);
        org.junit.jupiter.api.Assertions.assertNotNull(app);
    }
}
