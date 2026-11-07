package eu.wiegandt.librehousehold;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.grafana.LgtmStackContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Starts Grafana LGTM to receive the application's OpenTelemetry data during local development
 * ({@link TestLibrehouseholdApplication}). Deliberately separate from
 * {@link TestcontainersConfiguration}: no test asserts on telemetry, and starting this large image
 * for every test application context noticeably slows down the test suite.
 */
@TestConfiguration(proxyBeanMethods = false)
public class LgtmTestcontainersConfiguration {

    @Bean
    @ServiceConnection
    LgtmStackContainer grafanaLgtmContainer() {
        return new LgtmStackContainer(DockerImageName.parse("grafana/otel-lgtm:latest"));
    }

}
