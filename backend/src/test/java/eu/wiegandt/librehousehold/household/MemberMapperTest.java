package eu.wiegandt.librehousehold.household;

import eu.wiegandt.librehousehold.model.Member;
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

class MemberMapperTest {

    private final MemberMapper mapper = Mappers.getMapper(MemberMapper.class);

    private static final UUID MEMBER_ID = UUID.fromString("c3d4e5f6-a7b8-9012-cdef-012345678902");

    @Nested
    class toMemberEntity {

        static Stream<Arguments> inputs() {
            return Stream.of(
                    Arguments.of(
                            new Member(MEMBER_ID, "Alice", "alice@example.com").avatar("base64avatar"),
                            new MemberEntity(MEMBER_ID, "Alice", "alice@example.com", "base64avatar")
                    ),
                    Arguments.of(
                            new Member(MEMBER_ID, "Alice", "alice@example.com"),
                            new MemberEntity(MEMBER_ID, "Alice", "alice@example.com", null)
                    ),
                    Arguments.of(null, null)
            );
        }

        @ParameterizedTest
        @MethodSource("inputs")
        void input_mapsToEntity(Member input, MemberEntity expected) {
            assertThat(mapper.toMemberEntity(input)).isEqualTo(expected);
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
