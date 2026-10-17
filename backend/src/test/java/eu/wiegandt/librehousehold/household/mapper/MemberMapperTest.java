package eu.wiegandt.librehousehold.household.mapper;

import eu.wiegandt.librehousehold.household.model.MemberEntity;
import eu.wiegandt.librehousehold.model.Member;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mapstruct.factory.Mappers;

import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

class MemberMapperTest {

    private final MemberMapper mapper = Mappers.getMapper(MemberMapper.class);

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

    @Nested
    class toMemberEntity {

        @Test
        void memberWithAvatar_mapsAllFieldsCorrectly() {
            // given
            var memberId = UUID.fromString("11111111-1111-1111-1111-111111111111");
            var householdId = UUID.fromString("22222222-2222-2222-2222-222222222222");
            var member = new Member(memberId, "Max Mustermann", "max@example.com")
                    .avatar("data:image/png;base64,abc");
            var expected = new MemberEntity(memberId, "Max Mustermann", "max@example.com",
                    "data:image/png;base64,abc", householdId, true);

            // when
            var result = mapper.toMemberEntity(member, householdId, true);

            // then
            assertThat(result).usingRecursiveComparison().isEqualTo(expected);
        }

        @Test
        void memberWithoutAvatar_mapsAvatarToNull() {
            // given
            var memberId = UUID.fromString("11111111-1111-1111-1111-111111111111");
            var householdId = UUID.fromString("22222222-2222-2222-2222-222222222222");
            var member = new Member(memberId, "Max Mustermann", "max@example.com");
            var expected = new MemberEntity(memberId, "Max Mustermann", "max@example.com", null, householdId, false);

            // when
            var result = mapper.toMemberEntity(member, householdId, false);

            // then
            assertThat(result).usingRecursiveComparison().isEqualTo(expected);
        }
    }

    @Nested
    class toMemberEntityWithEmail {

        static Stream<Arguments> members() {
            var memberId = UUID.fromString("11111111-1111-1111-1111-111111111111");
            var householdId = UUID.fromString("22222222-2222-2222-2222-222222222222");
            return Stream.of(
                    argumentSet("maximal member",
                            new Member(memberId, "Max Mustermann", "typed@example.com").avatar("data:image/png;base64,abc"),
                            householdId, true, "verified@example.com",
                            new MemberEntity(memberId, "Max Mustermann", "verified@example.com",
                                    "data:image/png;base64,abc", householdId, true)),
                    argumentSet("minimal member",
                            new Member(memberId, "Max Mustermann", "typed@example.com"),
                            householdId, false, "verified@example.com",
                            new MemberEntity(memberId, "Max Mustermann", "verified@example.com", null, householdId, false)),
                    argumentSet("null input", null, null, false, null, null));
        }

        @ParameterizedTest
        @MethodSource("members")
        void member_mapsWithGivenEmailInsteadOfMemberEmail(Member member, UUID householdId, boolean isAdmin,
                                                          String email, MemberEntity expected) {
            // when
            var result = mapper.toMemberEntityWithEmail(member, householdId, isAdmin, email);

            // then
            assertThat(result).usingRecursiveComparison().isEqualTo(expected);
        }
    }
}
