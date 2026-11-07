package eu.wiegandt.librehousehold.realtime.model;

import eu.wiegandt.librehousehold.realtime.AsyncApiContract;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Keeps the handwritten signal types in line with {@code api/asyncapi.yml}, the single source of
 * truth for the event stream (ADR-019): no code is generated from it, so this test is what catches
 * a drift between contract and code.
 */
class SignalTypeContractTest {

    @Test
    void wireName_allSignalTypes_equalAsyncApiMessageNames() {
        // given
        var messageNames = AsyncApiContract.load().messageNames();

        // when
        var wireNames = Arrays.stream(SignalType.values()).map(SignalType::wireName).toList();

        // then
        assertThat(wireNames).containsExactlyInAnyOrderElementsOf(messageNames);
    }

    @Test
    void wireName_allSignalEntities_equalAsyncApiEntityEnum() {
        // given
        var entityEnum = AsyncApiContract.load().schemaEnum("Entity");

        // when
        var wireNames = Arrays.stream(SignalEntity.values()).map(SignalEntity::wireName).toList();

        // then
        assertThat(wireNames).containsExactlyInAnyOrderElementsOf(entityEnum);
    }

    @ParameterizedTest
    @MethodSource("signalRecords")
    void recordComponents_signalRecord_equalAsyncApiSchemaProperties(Class<? extends Record> signal,
                                                                     String schemaName) {
        // given
        var schemaProperties = AsyncApiContract.load().schemaProperties(schemaName);

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
}
