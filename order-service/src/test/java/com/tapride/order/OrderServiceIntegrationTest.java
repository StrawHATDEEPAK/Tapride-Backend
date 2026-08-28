package com.tapride.order;

import com.tapride.order.api.dto.RideRequestDTO;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real Testcontainers integration test - actual Postgres and Kafka containers,
 * actual Spring context, actual HTTP requests through TestRestTemplate. This
 * is deliberately heavier than the unit tests (RideStateMachineTest etc.) and
 * proves something they can't: that Flyway migrations apply cleanly against a
 * real Postgres, that the Kafka producer/consumer config actually connects,
 * and that the full request -> validate -> persist -> publish chain works
 * together, not just each piece in isolation.
 *
 * Note: this only exercises order-service alone (no payment/matching-service
 * running), so a ride can only get as far as PAYMENT_PENDING here - that's
 * the correct, expected outcome for this test, not a limitation to fix.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class OrderServiceIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("order_db")
            .withUsername("order_user")
            .withPassword("order_pass");

    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1"));

    // Overrides application.yml's defaults with the real, ephemeral container
    // endpoints - this runs before the Spring context boots, so the app
    // connects to these test containers instead of localhost:5433/localhost:9092.
    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @LocalServerPort
    private int port;

    private final TestRestTemplate restTemplate = new TestRestTemplate();

    @Test
    void requesting_a_ride_persists_it_and_reaches_payment_pending() {
        RideRequestDTO request = new RideRequestDTO(UUID.randomUUID(), 22.72, 75.86, 22.75, 75.90);

        ResponseEntity<String> response = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/rides", request, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        // By the time the HTTP response returns, requestRide() has already run
        // RIDE_REQUESTED -> RIDE_VALIDATED -> PAYMENT_AUTHORIZATION_REQUESTED
        // synchronously (see RideService) - with no payment-service running in
        // this test, PAYMENT_PENDING is exactly as far as it CAN legitimately go.
        assertThat(response.getBody()).contains("\"status\":\"PAYMENT_PENDING\"");
        assertThat(response.getBody()).contains("\"pickupLat\":22.72");
    }

    @Test
    void requesting_a_ride_with_identical_pickup_and_dropoff_fails_validation() {
        RideRequestDTO request = new RideRequestDTO(UUID.randomUUID(), 22.72, 75.86, 22.72, 75.86);

        ResponseEntity<String> response = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/rides", request, String.class);

        // The ride row IS created (that's the correct behavior - we record the
        // attempt), it just lands in VALIDATION_FAILED rather than progressing.
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).contains("\"status\":\"VALIDATION_FAILED\"");
    }

    @Test
    void looking_up_a_nonexistent_ride_returns_404_not_503() {
        // This specifically exercises the circuit-breaker ignore-exceptions fix
        // (see application.yml) - a plain 404 must NOT be treated as an infra
        // failure by the circuit breaker/retry config.
        ResponseEntity<String> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/api/rides/" + UUID.randomUUID(), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
