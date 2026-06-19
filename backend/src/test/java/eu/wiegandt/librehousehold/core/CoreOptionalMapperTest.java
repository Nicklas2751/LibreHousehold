package eu.wiegandt.librehousehold.core;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("OptionalAssignedToNull")
class CoreOptionalMapperTest {

    private final CoreOptionalMapper mapper = new CoreOptionalMapper() {
    };

    @Nested
    class toOptionalString {

        @Test
        void nonNullValue_returnsOptionalContainingValue() {
            assertThat(mapper.toOptionalString("hello")).contains("hello");
        }

        @Test
        void nullValue_returnsEmptyOptional() {
            assertThat(mapper.toOptionalString(null)).isEmpty();
        }
    }

    @Nested
    class fromOptionalString {

        @Test
        void presentOptional_returnsValue() {
            assertThat(mapper.fromOptionalString(Optional.of("hello"))).isEqualTo("hello");
        }

        @Test
        void emptyOptional_returnsNull() {
            assertThat(mapper.fromOptionalString(Optional.empty())).isNull();
        }

        @Test
        void nullOptional_returnsNull() {
            assertThat(mapper.fromOptionalString(null)).isNull();
        }
    }

    @Nested
    class fromOptionalUuid {

        @Test
        void presentOptional_returnsUuid() {
            var id = UUID.randomUUID();
            assertThat(mapper.fromOptionalUuid(Optional.of(id))).isEqualTo(id);
        }

        @Test
        void emptyOptional_returnsNull() {
            assertThat(mapper.fromOptionalUuid(Optional.empty())).isNull();
        }

        @Test
        void nullOptional_returnsNull() {
            assertThat(mapper.fromOptionalUuid(null)).isNull();
        }
    }

    @Nested
    class fromOptionalLocalDate {

        @Test
        void presentOptional_returnsDate() {
            var date = LocalDate.of(2026, 6, 19);
            assertThat(mapper.fromOptionalLocalDate(Optional.of(date))).isEqualTo(date);
        }

        @Test
        void emptyOptional_returnsNull() {
            assertThat(mapper.fromOptionalLocalDate(Optional.empty())).isNull();
        }

        @Test
        void nullOptional_returnsNull() {
            assertThat(mapper.fromOptionalLocalDate(null)).isNull();
        }
    }

    @Nested
    class toOptionalBoolean {

        @Test
        void trueValue_returnsPresentTrueOptional() {
            assertThat(mapper.toOptionalBoolean(true)).contains(true);
        }

        @Test
        void falseValue_returnsPresentFalseOptional() {
            assertThat(mapper.toOptionalBoolean(false)).contains(false);
        }
    }

    @Nested
    class isPresent {

        @Test
        void presentOptional_returnsTrue() {
            assertThat(mapper.isPresent(Optional.of("value"))).isTrue();
        }

        @Test
        void emptyOptional_returnsFalse() {
            assertThat(mapper.isPresent(Optional.empty())).isFalse();
        }

        @Test
        void nullOptional_returnsFalse() {
            assertThat(mapper.isPresent(null)).isFalse();
        }
    }
}
