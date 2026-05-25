package eu.wiegandt.librehousehold.household;

import eu.wiegandt.librehousehold.TestcontainersConfiguration;
import eu.wiegandt.librehousehold.model.Household;
import eu.wiegandt.librehousehold.model.HouseholdSetup;
import eu.wiegandt.librehousehold.model.HouseholdUpdate;
import eu.wiegandt.librehousehold.model.Member;
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
class HouseholdManagementServiceIT {

    @Autowired
    private HouseholdManagementService managementService;

    @Autowired
    private HouseholdSetupService setupService;

    @Autowired
    private HouseholdRepository householdRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private InviteRepository inviteRepository;

    private Household existingHousehold;

    @BeforeEach
    void setUp() {
        var member = Instancio.create(Member.class);
        existingHousehold = Instancio.of(Household.class)
                .set(field(Household::getAdmin), member.getId())
                .create();
        setupService.setupHousehold(new HouseholdSetup(existingHousehold, member));
    }

    @Nested
    class updateName {

        @Test
        void householdNotFound_throwsHouseholdNotFoundException() {
            // given
            var unknownId = Instancio.create(UUID.class);

            // when / then
            assertThatThrownBy(() -> managementService.updateName(unknownId, new HouseholdUpdate("X")))
                    .isInstanceOf(HouseholdNotFoundException.class);
        }

        @Test
        void validUpdate_persistsNewNameInDatabase() {
            // given
            var newName = "Updated Household Name";

            // when
            managementService.updateName(existingHousehold.getId(), new HouseholdUpdate(newName));

            // then
            assertThat(householdRepository.findById(existingHousehold.getId()))
                    .hasValueSatisfying(h -> assertThat(h.name()).isEqualTo(newName));
        }
    }

    @Nested
    class deleteHousehold {

        @Test
        void householdNotFound_throwsHouseholdNotFoundException() {
            // given
            var unknownId = Instancio.create(UUID.class);

            // when / then
            assertThatThrownBy(() -> managementService.deleteHousehold(unknownId))
                    .isInstanceOf(HouseholdNotFoundException.class);
        }

        @Test
        void householdFound_removesHouseholdFromDatabase() {
            // when
            managementService.deleteHousehold(existingHousehold.getId());

            // then
            assertThat(householdRepository.existsById(existingHousehold.getId())).isFalse();
        }

        @Test
        void householdFound_removesAssociatedInviteFromDatabase() {
            // when
            managementService.deleteHousehold(existingHousehold.getId());

            // then
            assertThat(inviteRepository.findByHouseholdId(existingHousehold.getId())).isEmpty();
        }
    }

    @Nested
    class getInvite {

        @Test
        void noInviteExists_throwsHouseholdNotFoundException() {
            // given
            var unknownId = Instancio.create(UUID.class);

            // when / then
            assertThatThrownBy(() -> managementService.getInvite(unknownId))
                    .isInstanceOf(HouseholdNotFoundException.class);
        }

        @Test
        void inviteExists_returnsStoredToken() {
            // given
            var storedInvite = inviteRepository.findByHouseholdId(existingHousehold.getId()).orElseThrow();

            // when
            var result = managementService.getInvite(existingHousehold.getId());

            // then
            assertThat(result.getInviteToken()).isEqualTo(storedInvite.token());
        }
    }

    @Nested
    class regenerateInvite {

        @Test
        void householdNotFound_throwsHouseholdNotFoundException() {
            // given
            var unknownId = Instancio.create(UUID.class);

            // when / then
            assertThatThrownBy(() -> managementService.regenerateInvite(unknownId))
                    .isInstanceOf(HouseholdNotFoundException.class);
        }

        @Test
        void householdFound_oldTokenIsInvalidated() {
            // given
            var oldToken = inviteRepository.findByHouseholdId(existingHousehold.getId())
                    .orElseThrow().token();

            // when
            managementService.regenerateInvite(existingHousehold.getId());

            // then — only one invite exists and it has a new token
            var invites = inviteRepository.findByHouseholdId(existingHousehold.getId());
            assertThat(invites).hasValueSatisfying(invite -> assertThat(invite.token()).isNotEqualTo(oldToken));
        }

        @Test
        void householdFound_newTokenIsValidForSevenDays() {
            // given
            var expectedValidUntil = LocalDate.now().plusDays(7);

            // when
            var result = managementService.regenerateInvite(existingHousehold.getId());

            // then
            assertThat(result.getInviteValidUntil()).isEqualTo(expectedValidUntil);
        }
    }

    @Nested
    class transferOwnership {

        @Test
        void householdNotFound_throwsHouseholdNotFoundException() {
            // given
            var unknownId = Instancio.create(UUID.class);

            // when / then
            assertThatThrownBy(() -> managementService.transferOwnership(unknownId, Instancio.create(UUID.class)))
                    .isInstanceOf(HouseholdNotFoundException.class);
        }

        @Test
        void householdFound_updatesAdminInDatabase() {
            // given — persist a second member who will become the new admin
            var newAdmin = memberRepository.save(Instancio.create(MemberEntity.class));

            // when
            managementService.transferOwnership(existingHousehold.getId(), newAdmin.id());

            // then
            assertThat(householdRepository.findById(existingHousehold.getId()))
                    .hasValueSatisfying(h -> assertThat(h.adminId()).isEqualTo(newAdmin.id()));
        }
    }
}
