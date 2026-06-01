package eu.wiegandt.librehousehold.household;

import eu.wiegandt.librehousehold.TestcontainersConfiguration;
import eu.wiegandt.librehousehold.model.Household;
import eu.wiegandt.librehousehold.model.HouseholdSetup;
import eu.wiegandt.librehousehold.model.Member;
import org.instancio.Instancio;
import org.instancio.junit.InstancioExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.time.LocalDate;

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

    @Test
    void setupHousehold_validSetup_persistsHouseholdInDatabase() {
        // given
        var member = Instancio.create(Member.class);
        var household = Instancio.create(Household.class);

        // when
        service.setupHousehold(new HouseholdSetup(household, member));

        // then
        assertThat(householdRepository.findById(household.getId()))
                .contains(householdSetupMapper.toHouseholdEntity(household));
    }

    @Test
    void setupHousehold_validSetup_persistsMemberInDatabase() {
        // given
        var member = Instancio.create(Member.class);
        var household = Instancio.create(Household.class);

        // when
        service.setupHousehold(new HouseholdSetup(household, member));

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
        service.setupHousehold(new HouseholdSetup(household, member1));

        var member2 = Instancio.create(Member.class);
        var conflictingHousehold = Instancio.of(Household.class)
                .set(field(Household::getId), household.getId())
                .create();

        // when / then
        assertThatThrownBy(() -> service.setupHousehold(new HouseholdSetup(conflictingHousehold, member2)))
                .isInstanceOf(HouseholdAlreadyExistsException.class);
    }

    @Test
    void setupHousehold_duplicateMemberEmail_throwsMemberAlreadyExistsException() {
        // given
        var member1 = Instancio.create(Member.class);
        var household1 = Instancio.create(Household.class);
        service.setupHousehold(new HouseholdSetup(household1, member1));

        var member2 = Instancio.of(Member.class)
                .set(field(Member::getEmail), member1.getEmail())
                .create();
        var household2 = Instancio.create(Household.class);

        // when / then
        assertThatThrownBy(() -> service.setupHousehold(new HouseholdSetup(household2, member2)))
                .isInstanceOf(MemberAlreadyExistsException.class);
    }

    @Test
    void setupHousehold_validSetup_inviteTokenStoredInDatabase() {
        // given
        var member = Instancio.create(Member.class);
        var household = Instancio.create(Household.class);

        // when
        var result = service.setupHousehold(new HouseholdSetup(household, member));

        // then
        assertThat(inviteRepository.findAll())
                .anyMatch(invite -> invite.token().equals(result.getInviteToken()));
    }

    @Test
    void setupHousehold_validSetup_inviteValidForSevenDays() {
        // given
        var member = Instancio.create(Member.class);
        var household = Instancio.create(Household.class);
        var expectedValidUntil = LocalDate.now().plusDays(7);

        // when
        var result = service.setupHousehold(new HouseholdSetup(household, member));

        // then
        assertThat(inviteRepository.findAll())
                .anyMatch(invite -> invite.token().equals(result.getInviteToken())
                        && invite.validUntil().equals(expectedValidUntil));
    }
}
