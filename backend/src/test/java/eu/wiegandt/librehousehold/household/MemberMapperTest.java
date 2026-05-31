package eu.wiegandt.librehousehold.household;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MemberMapperTest {

    private final MemberMapper mapper = Mappers.getMapper(MemberMapper.class);

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

    @Nested
    class toOptionalBoolean {

        @Test
        void trueValue_returnsOptionalWithTrue() {
            assertThat(mapper.toOptionalBoolean(true)).contains(true);
        }

        @Test
        void falseValue_returnsOptionalWithFalse() {
            assertThat(mapper.toOptionalBoolean(false)).contains(false);
        }
    }

    @Nested
    class toMember {

        @Test
        void entity_mapsAllFieldsCorrectly() {
            // given
            var entity = new MemberEntity(
                    UUID.fromString("11111111-1111-1111-1111-111111111111"),
                    "Max Mustermann",
                    "max@example.com",
                    "data:image/png;base64,abc",
                    UUID.fromString("22222222-2222-2222-2222-222222222222"),
                    true
            );

            // when
            var result = mapper.toMember(entity);

            // then
            assertThat(result.getId()).isEqualTo(entity.id());
            assertThat(result.getName()).isEqualTo(entity.name());
            assertThat(result.getEmail()).isEqualTo(entity.email());
            assertThat(result.getAvatar()).contains(entity.avatar());
            assertThat(result.getIsAdmin()).contains(true);
        }
    }
}
