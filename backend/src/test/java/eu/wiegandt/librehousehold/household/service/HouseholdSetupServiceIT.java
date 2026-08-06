package eu.wiegandt.librehousehold.household.service;
import eu.wiegandt.librehousehold.household.exception.*;
import eu.wiegandt.librehousehold.household.mapper.*;
import eu.wiegandt.librehousehold.household.model.*;
import eu.wiegandt.librehousehold.household.repository.*;

import eu.wiegandt.librehousehold.TestcontainersConfiguration;
import eu.wiegandt.librehousehold.model.Household;
import eu.wiegandt.librehousehold.model.HouseholdSetup;
import eu.wiegandt.librehousehold.model.LocalRegistration;
import eu.wiegandt.librehousehold.model.Member;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.instancio.Instancio;
import org.instancio.junit.InstancioExtension;
import org.junit.jupiter.api.AfterEach;
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
class HouseholdSetupServiceIT {

    @Autowired
    private HouseholdSetupService service;

    @Autowired
    private HouseholdRepository householdRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private InviteRepository inviteRepository;

    @Autowired
    private HouseholdSetupMapper householdSetupMapper;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private UUID createdHouseholdId;

    @AfterEach
    void tearDown() {
        if (createdHouseholdId != null) {
            inviteRepository.deleteByHouseholdId(createdHouseholdId);
            memberRepository.deleteByHouseholdId(createdHouseholdId);
            householdRepository.deleteById(createdHouseholdId);
        }
    }

    @Test
    void setupHousehold_validSetup_persistsHashedPasswordInAccountTable() {
        // given
        var member = Instancio.create(Member.class);
        var household = Instancio.create(Household.class);
        var localRegistration = Instancio.create(LocalRegistration.class);
        createdHouseholdId = household.getId();

        // when
        service.setupHousehold(new HouseholdSetup(household, member, localRegistration));

        // then
        assertThat(accountRepository.findById(member.getId()))
                .hasValueSatisfying(account ->
                        assertThat(passwordEncoder.matches(localRegistration.getPassword(), account.passwordHash())).isTrue());
    }

    @Test
    void setupHousehold_validSetup_persistsHouseholdInDatabase() {
        // given
        var member = Instancio.create(Member.class);
        var household = Instancio.create(Household.class);
        var localRegistration = Instancio.create(LocalRegistration.class);
        createdHouseholdId = household.getId();

        // when
        service.setupHousehold(new HouseholdSetup(household, member, localRegistration));

        // then
        assertThat(householdRepository.findById(household.getId()))
                .contains(householdSetupMapper.toHouseholdEntity(household));
    }

    @Test
    void setupHousehold_validSetup_persistsMemberInDatabase() {
        // given
        var member = Instancio.create(Member.class);
        var household = Instancio.create(Household.class);
        var localRegistration = Instancio.create(LocalRegistration.class);
        createdHouseholdId = household.getId();

        // when
        service.setupHousehold(new HouseholdSetup(household, member, localRegistration));

        // then
        assertThat(memberRepository.findById(member.getId()))
                .hasValueSatisfying(m -> {
                    assertThat(m.householdId()).isEqualTo(household.getId());
                    assertThat(m.isAdmin()).isTrue();
                });
    }

    @Test
    void setupHousehold_duplicateHouseholdId_throwsHouseholdAlreadyExistsException() {
        // given
        var member1 = Instancio.create(Member.class);
        var household = Instancio.create(Household.class);
        createdHouseholdId = household.getId();
        service.setupHousehold(new HouseholdSetup(household, member1, Instancio.create(LocalRegistration.class)));

        var member2 = Instancio.create(Member.class);
        var conflictingHousehold = Instancio.of(Household.class)
                .set(field(Household::getId), household.getId())
                .create();
        var localRegistration = Instancio.create(LocalRegistration.class);

        // when / then
        assertThatThrownBy(() -> service.setupHousehold(new HouseholdSetup(conflictingHousehold, member2, localRegistration)))
                .isInstanceOf(HouseholdAlreadyExistsException.class);
    }

    @Test
    void setupHousehold_duplicateMemberEmail_throwsMemberAlreadyExistsException() {
        // given
        var member1 = Instancio.create(Member.class);
        var household1 = Instancio.create(Household.class);
        createdHouseholdId = household1.getId();
        service.setupHousehold(new HouseholdSetup(household1, member1, Instancio.create(LocalRegistration.class)));

        var member2 = Instancio.of(Member.class)
                .set(field(Member::getEmail), member1.getEmail())
                .create();
        var household2 = Instancio.create(Household.class);
        var localRegistration = Instancio.create(LocalRegistration.class);

        // when / then
        assertThatThrownBy(() -> service.setupHousehold(new HouseholdSetup(household2, member2, localRegistration)))
                .isInstanceOf(MemberAlreadyExistsException.class);
    }

    @Test
    void setupHousehold_validSetup_inviteTokenStoredInDatabase() {
        // given
        var member = Instancio.create(Member.class);
        var household = Instancio.create(Household.class);
        var localRegistration = Instancio.create(LocalRegistration.class);
        createdHouseholdId = household.getId();

        // when
        var result = service.setupHousehold(new HouseholdSetup(household, member, localRegistration));

        // then
        assertThat(inviteRepository.findAll())
                .anyMatch(invite -> invite.token().equals(result.getInviteToken()));
    }

    @Test
    void setupHousehold_validSetup_inviteValidForSevenDays() {
        // given
        var member = Instancio.create(Member.class);
        var household = Instancio.create(Household.class);
        var localRegistration = Instancio.create(LocalRegistration.class);
        var expectedValidUntil = LocalDate.now().plusDays(7);
        createdHouseholdId = household.getId();

        // when
        var result = service.setupHousehold(new HouseholdSetup(household, member, localRegistration));

        // then
        assertThat(inviteRepository.findAll())
                .anyMatch(invite -> invite.token().equals(result.getInviteToken())
                        && invite.validUntil().equals(expectedValidUntil));
    }
}
