package eu.wiegandt.librehousehold.realtime.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Keeps the handwritten signal types in line with {@code api/asyncapi.yml}, the single source of
 * truth for the event stream (ADR-019): no code is generated from it, so this test is what catches
 * a drift between contract and code.
 */
class SignalTypeContractTest {

    // Relative to the backend module, which is the working directory of Surefire
    private static final Path ASYNCAPI_SPEC = Path.of("../api/asyncapi.yml");

    @Test
    void wireName_allSignalTypes_equalAsyncApiMessageNames() throws IOException {
        // given
        var messageNames = section(components(), "messages").values().stream()
                .map(SignalTypeContractTest::messageName)
                .toList();

        // when
        var wireNames = Arrays.stream(SignalType.values()).map(SignalType::wireName).toList();

        // then
        assertThat(wireNames).containsExactlyInAnyOrderElementsOf(messageNames);
    }

    @Test
    void wireName_allSignalEntities_equalAsyncApiEntityEnum() throws IOException {
        // given
        @SuppressWarnings("unchecked")
        var entityEnum = (List<String>) section(components(), "schemas", "Entity").get("enum");

        // when
        var wireNames = Arrays.stream(SignalEntity.values()).map(SignalEntity::wireName).toList();

        // then
        assertThat(wireNames).containsExactlyInAnyOrderElementsOf(entityEnum);
    }

    @ParameterizedTest
    @MethodSource("signalRecords")
    void recordComponents_signalRecord_equalAsyncApiSchemaProperties(Class<? extends Record> signal, String schemaName)
            throws IOException {
        // given
        var schemaProperties = section(components(), "schemas", schemaName, "properties").keySet();

        // when
        var componentNames = Arrays.stream(signal.getRecordComponents()).map(RecordComponent::getName).toList();

        // then
        assertThat(componentNames).containsExactlyInAnyOrderElementsOf(schemaProperties);
    }

    private static Stream<Arguments> signalRecords() {
        return Stream.of(
                Arguments.of(InvalidationSignal.class, "InvalidationSignal"),
                Arguments.of(SessionTerminatedSignal.class, "SessionTerminatedSignal"));
    }

    private static Map<String, Object> components() throws IOException {
        try (var spec = Files.newInputStream(ASYNCAPI_SPEC)) {
            Map<String, Object> root = new Yaml().load(spec);
            return section(root, "components");
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> section(Map<String, Object> parent, String... path) {
        var current = parent;
        for (var key : path) {
            current = (Map<String, Object>) current.get(key);
        }
        return current;
    }

    private static String messageName(Object message) {
        return (String) ((Map<?, ?>) message).get("name");
    }
}
