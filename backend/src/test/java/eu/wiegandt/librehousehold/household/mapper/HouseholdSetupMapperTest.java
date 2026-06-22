package eu.wiegandt.librehousehold.household.mapper;
import eu.wiegandt.librehousehold.household.model.*;
import eu.wiegandt.librehousehold.household.mapper.*;

import eu.wiegandt.librehousehold.model.Household;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mapstruct.factory.Mappers;

import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class HouseholdSetupMapperTest {

    private final HouseholdSetupMapper mapper = Mappers.getMapper(HouseholdSetupMapper.class);

    private static final UUID HOUSEHOLD_ID = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890");

    @Nested
    class toHouseholdEntity {

        static Stream<Arguments> inputs() {
            return Stream.of(
                    Arguments.of(
                            new Household(HOUSEHOLD_ID, "Smith Family").image("base64image"),
                            new HouseholdEntity(HOUSEHOLD_ID, "Smith Family", "base64image")
                    ),
                    Arguments.of(
                            new Household(HOUSEHOLD_ID, "Smith Family"),
                            new HouseholdEntity(HOUSEHOLD_ID, "Smith Family", null)
                    ),
                    Arguments.of(null, null)
            );
        }

        @ParameterizedTest
        @MethodSource("inputs")
        void input_mapsToEntity(Household input, HouseholdEntity expected) {
            assertThat(mapper.toHouseholdEntity(input)).isEqualTo(expected);
        }
    }

    @Nested
    class toApiModel {

        @Test
        void entityWithImage_mapsToHousehold() {
            // given
            var entity = new HouseholdEntity(HOUSEHOLD_ID, "Smith Family", "base64image");
            var expected = new Household(HOUSEHOLD_ID, "Smith Family").image("base64image");

            // when / then
            assertThat(mapper.toApiModel(entity)).isEqualTo(expected);
        }

        @Test
        void entityWithoutImage_mapsToHousehold() {
            // given
            var entity = new HouseholdEntity(HOUSEHOLD_ID, "Smith Family", null);
            var expected = new Household(HOUSEHOLD_ID, "Smith Family");

            // when / then
            assertThat(mapper.toApiModel(entity)).isEqualTo(expected);
        }
    }

    @Nested
    class fromOptional {

        @Test
        void emptyOptional_returnsNull() {
            assertThat(mapper.fromOptional(Optional.empty())).isNull();
        }

        @Test
        void presentOptional_returnsValue() {
            assertThat(mapper.fromOptional(Optional.of("value"))).isEqualTo("value");
        }
    }

    @Nested
    class toOptional {

        @Test
        void null_returnsEmptyOptional() {
            assertThat(mapper.toOptional(null)).isEmpty();
        }

        @Test
        void value_returnsOptionalWithValue() {
            assertThat(mapper.toOptional("value")).contains("value");
        }
    }
}
