package eu.wiegandt.librehousehold.realtime.model;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Uses the JsonMapper as Spring Boot configures it, since SseEmitter serializes the payload with
 * the same mapper through the MVC message converters.
 */
@JsonTest
class InvalidationSignalSerializationTest {

    @Autowired
    private JsonMapper jsonMapper;

    @Test
    void writeValueAsString_invalidationSignal_singleLineJsonWithExactlyTheContractFields() {
        // given
        var signal = new InvalidationSignal(SignalEntity.TASK,
                UUID.fromString("7c1e2d3f-4a5b-4c6d-8e9f-0a1b2c3d4e5f"), Instant.parse("2026-11-07T18:30:00Z"));

        // when
        var json = jsonMapper.writeValueAsString(signal);

        // then
        assertThat(json).isEqualTo(
                "{\"entity\":\"task\",\"entityId\":\"7c1e2d3f-4a5b-4c6d-8e9f-0a1b2c3d4e5f\",\"occurredAt\":\"2026-11-07T18:30:00Z\"}");
    }
}
