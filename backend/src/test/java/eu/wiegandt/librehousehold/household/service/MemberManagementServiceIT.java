package eu.wiegandt.librehousehold.household.service;
import eu.wiegandt.librehousehold.household.exception.*;
import eu.wiegandt.librehousehold.household.mapper.*;
import eu.wiegandt.librehousehold.household.model.*;
import eu.wiegandt.librehousehold.household.repository.*;

import eu.wiegandt.librehousehold.TestcontainersConfiguration;
import eu.wiegandt.librehousehold.model.*;
import org.instancio.Instancio;
import org.instancio.junit.InstancioExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.instancio.Select.field;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import(TestcontainersConfiguration.class)
@ExtendWith(InstancioExtension.class)
class MemberManagementServiceIT {

    @Autowired
    private MemberManagementService memberManagementService;

    @Autowired
    private HouseholdSetupService setupService;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private InviteRepository inviteRepository;

    private HouseholdSetupResponse setupResponse;
    private Household existingHousehold;

    @BeforeEach
    void setUp() {
        var member = Instancio.create(Member.class);
        existingHousehold = Instancio.create(Household.class);
        setupResponse = setupService.setupHousehold(new HouseholdSetup(existingHousehold, member));
    }

    @Nested
    class getMembers {

        @Test
        void adminIsOnlyMember_returnsOneAdminMember() {
            // when
            var result = memberManagementService.getMembers(existingHousehold.getId());

            // then
            assertThat(result).hasSize(1);
            assertThat(result.getFirst().getIsAdmin()).contains(true);
        }

        @Test
        void additionalMemberJoined_returnsBothMembers() {
            // given
            var token = setupResponse.getInviteToken();
            var registration = Instancio.create(MemberRegistration.class);
            memberManagementService.joinHousehold(token, registration);

            // when
            var result = memberManagementService.getMembers(existingHousehold.getId());

            // then
            assertThat(result).hasSize(2);
        }
    }

    @Nested
    class joinHousehold {

        @Test
        void validToken_persistsMemberInDatabase() {
            // given
            var token = setupResponse.getInviteToken();
            var registration = Instancio.create(MemberRegistration.class);

            // when
            memberManagementService.joinHousehold(token, registration);

            // then
            assertThat(memberRepository.findById(registration.getId()))
                    .hasValueSatisfying(m -> {
                        assertThat(m.householdId()).isEqualTo(existingHousehold.getId());
                        assertThat(m.isAdmin()).isFalse();
                    });
        }

        @Test
        void expiredToken_throwsInvalidInviteException() {
            // given — create an expired invite directly
            var expiredInvite = inviteRepository.save(new InviteEntity(
                    null, existingHousehold.getId(), UUID.randomUUID(), LocalDate.now().minusDays(1)
            ));
            var registration = Instancio.create(MemberRegistration.class);

            // when / then
            assertThatThrownBy(() -> memberManagementService.joinHousehold(expiredInvite.token(), registration))
                    .isInstanceOf(InvalidInviteException.class);
        }

    }

    @Nested
    class removeMember {

        @Test
        void memberFound_removesFromDatabase() {
            // given
            var token = setupResponse.getInviteToken();
            var registration = Instancio.create(MemberRegistration.class);
            memberManagementService.joinHousehold(token, registration);

            // when
            memberManagementService.removeMember(registration.getId());

            // then
            assertThat(memberRepository.existsById(registration.getId())).isFalse();
        }

        @Test
        void memberNotFound_throwsMemberNotFoundException() {
            // when / then
            assertThatThrownBy(() -> memberManagementService.removeMember(UUID.randomUUID()))
                    .isInstanceOf(MemberNotFoundException.class);
        }
    }
}
