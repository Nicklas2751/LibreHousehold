package eu.wiegandt.librehousehold.household.service;

import eu.wiegandt.librehousehold.TestcontainersConfiguration;
import eu.wiegandt.librehousehold.household.exception.HouseholdNotFoundException;
import eu.wiegandt.librehousehold.household.exception.MemberNotFoundException;
import eu.wiegandt.librehousehold.household.model.HouseholdEntity;
import eu.wiegandt.librehousehold.household.model.MemberEntity;
import eu.wiegandt.librehousehold.household.repository.HouseholdRepository;
import eu.wiegandt.librehousehold.household.repository.InviteRepository;
import eu.wiegandt.librehousehold.household.repository.MemberRepository;
import eu.wiegandt.librehousehold.model.*;
import org.instancio.Instancio;
import org.instancio.junit.InstancioExtension;
import org.junit.jupiter.api.AfterEach;
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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {"librehousehold.security.oauth2-client.client-secret=test-client-secret"})
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
        existingHousehold = Instancio.create(Household.class);
        var localRegistration = Instancio.create(LocalRegistration.class);
        setupService.setupHousehold(new HouseholdSetup(existingHousehold, member, localRegistration));
    }

    @AfterEach
    void tearDown() {
        // idempotent: some tests (e.g. deleteHousehold) already remove these rows themselves
        inviteRepository.deleteByHouseholdId(existingHousehold.getId());
        memberRepository.deleteByHouseholdId(existingHousehold.getId());
        householdRepository.deleteById(existingHousehold.getId());
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
        void householdWithoutAdmin_throwsHouseholdNotFoundException() {
            // given — a real household that has no admin (e.g. through a lost invariant); the FK on
            // member.household_id means a member can only ever reference a household that exists,
            // so this is the only realistic way to make revokeAdmin() affect zero rows
            var householdWithoutAdmin = householdRepository.save(Instancio.create(HouseholdEntity.class));
            var memberOfThatHousehold = memberRepository.save(Instancio.of(MemberEntity.class)
                    .set(field(MemberEntity::householdId), householdWithoutAdmin.id())
                    .set(field(MemberEntity::isAdmin), false)
                    .create());

            // when / then
            assertThatThrownBy(() -> managementService.transferOwnership(householdWithoutAdmin.id(), memberOfThatHousehold.id()))
                    .isInstanceOf(HouseholdNotFoundException.class);

            memberRepository.deleteById(memberOfThatHousehold.id());
            householdRepository.deleteById(householdWithoutAdmin.id());
        }

        @Test
        void newAdminBelongsToDifferentHousehold_throwsMemberNotFoundExceptionAndOriginalAdminKeepsAdminRights() {
            // given — a member belonging to a completely unrelated household
            var otherHousehold = householdRepository.save(Instancio.create(HouseholdEntity.class));
            var memberOfOtherHousehold = memberRepository.save(Instancio.of(MemberEntity.class)
                    .set(field(MemberEntity::householdId), otherHousehold.id())
                    .set(field(MemberEntity::isAdmin), false)
                    .create());
            var originalAdminId = memberRepository.findByHouseholdIdAndIsAdminTrue(existingHousehold.getId())
                    .orElseThrow().id();

            // when / then
            assertThatThrownBy(() -> managementService.transferOwnership(existingHousehold.getId(), memberOfOtherHousehold.id()))
                    .isInstanceOf(MemberNotFoundException.class);
            assertThat(memberRepository.findByHouseholdIdAndIsAdminTrue(existingHousehold.getId()))
                    .hasValueSatisfying(admin -> assertThat(admin.id()).isEqualTo(originalAdminId));

            memberRepository.deleteById(memberOfOtherHousehold.id());
            householdRepository.deleteById(otherHousehold.id());
        }

        @Test
        void householdFound_updatesAdminInDatabase() {
            // given — persist a second member in the same household who will become the new admin
            var newAdmin = memberRepository.save(Instancio.of(MemberEntity.class)
                    .set(field(MemberEntity::householdId), existingHousehold.getId())
                    .set(field(MemberEntity::isAdmin), false)
                    .create());

            // when
            managementService.transferOwnership(existingHousehold.getId(), newAdmin.id());

            // then
            assertThat(memberRepository.findByHouseholdIdAndIsAdminTrue(existingHousehold.getId()))
                    .hasValueSatisfying(m -> assertThat(m.id()).isEqualTo(newAdmin.id()));
        }
    }
}
