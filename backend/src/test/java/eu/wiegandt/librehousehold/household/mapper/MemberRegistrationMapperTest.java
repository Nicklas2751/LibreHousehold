package eu.wiegandt.librehousehold.household.mapper;

import eu.wiegandt.librehousehold.household.model.MemberEntity;
import eu.wiegandt.librehousehold.model.LocalRegistration;
import eu.wiegandt.librehousehold.model.MemberRegistration;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MemberRegistrationMapperTest {

    private final MemberRegistrationMapper mapper = Mappers.getMapper(MemberRegistrationMapper.class);

    @Nested
    class toMemberEntity {

        @Test
        void registrationWithAvatar_mapsAllFieldsCorrectly() {
            // given
            var memberId = UUID.fromString("11111111-1111-1111-1111-111111111111");
            var householdId = UUID.fromString("22222222-2222-2222-2222-222222222222");
            var registration = new MemberRegistration(memberId, "Max Mustermann", "max@example.com",
                    new LocalRegistration("correct horse battery staple"))
                    .avatar("data:image/png;base64,abc");
            var expected = new MemberEntity(memberId, "Max Mustermann", "max@example.com",
                    "data:image/png;base64,abc", householdId, false);

            // when
            var result = mapper.toMemberEntity(registration, householdId, false);

            // then
            assertThat(result).usingRecursiveComparison().isEqualTo(expected);
        }

        @Test
        void registrationWithoutAvatar_mapsAvatarToNull() {
            // given
            var memberId = UUID.fromString("11111111-1111-1111-1111-111111111111");
            var householdId = UUID.fromString("22222222-2222-2222-2222-222222222222");
            var registration = new MemberRegistration(memberId, "Max Mustermann", "max@example.com",
                    new LocalRegistration("correct horse battery staple"));
            var expected = new MemberEntity(memberId, "Max Mustermann", "max@example.com",
                    null, householdId, true);

            // when
            var result = mapper.toMemberEntity(registration, householdId, true);

            // then
            assertThat(result).usingRecursiveComparison().isEqualTo(expected);
        }
    }
}
