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

    @Autowired
    private MemberMapper memberMapper;

    @Test
    void setupHousehold_validSetup_persistsHouseholdInDatabase() {
        // given
        var member = Instancio.create(Member.class);
        var household = Instancio.of(Household.class)
                .set(field(Household::getAdmin), member.getId())
                .create();

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
        var household = Instancio.of(Household.class)
                .set(field(Household::getAdmin), member.getId())
                .create();

        // when
        service.setupHousehold(new HouseholdSetup(household, member));

        // then
        assertThat(memberRepository.findById(member.getId()))
                .contains(memberMapper.toMemberEntity(member));
    }

    @Test
    void setupHousehold_duplicateHouseholdId_throwsHouseholdAlreadyExistsException() {
        // given
        var member1 = Instancio.create(Member.class);
        var household = Instancio.of(Household.class)
                .set(field(Household::getAdmin), member1.getId())
                .create();
        service.setupHousehold(new HouseholdSetup(household, member1));

        var member2 = Instancio.create(Member.class);
        var conflictingHousehold = Instancio.of(Household.class)
                .set(field(Household::getId), household.getId())
                .set(field(Household::getAdmin), member2.getId())
                .create();

        // when / then
        assertThatThrownBy(() -> service.setupHousehold(new HouseholdSetup(conflictingHousehold, member2)))
                .isInstanceOf(HouseholdAlreadyExistsException.class);
    }

    @Test
    void setupHousehold_duplicateMemberEmail_throwsHouseholdAlreadyExistsException() {
        // given
        var member1 = Instancio.create(Member.class);
        var household1 = Instancio.of(Household.class)
                .set(field(Household::getAdmin), member1.getId())
                .create();
        service.setupHousehold(new HouseholdSetup(household1, member1));

        var member2 = Instancio.of(Member.class)
                .set(field(Member::getEmail), member1.getEmail())
                .create();
        var household2 = Instancio.of(Household.class)
                .set(field(Household::getAdmin), member2.getId())
                .create();

        // when / then
        assertThatThrownBy(() -> service.setupHousehold(new HouseholdSetup(household2, member2)))
                .isInstanceOf(HouseholdAlreadyExistsException.class);
    }

    @Test
    void setupHousehold_adminAlreadyHasHousehold_throwsHouseholdAlreadyExistsException() {
        // given — insert existing admin and their household directly via repositories
        var existingAdmin = memberRepository.save(Instancio.create(MemberEntity.class));
        householdRepository.save(Instancio.of(HouseholdEntity.class)
                .set(field(HouseholdEntity::adminId), existingAdmin.id())
                .create());

        // new member with different id and email, but household references the existing admin
        var newMember = Instancio.create(Member.class);
        var conflictingHousehold = Instancio.of(Household.class)
                .set(field(Household::getAdmin), existingAdmin.id())
                .create();

        // when / then
        assertThatThrownBy(() -> service.setupHousehold(new HouseholdSetup(conflictingHousehold, newMember)))
                .isInstanceOf(HouseholdAlreadyExistsException.class);
    }

    @Test
    void setupHousehold_validSetup_inviteTokenStoredInDatabase() {
        // given
        var member = Instancio.create(Member.class);
        var household = Instancio.of(Household.class)
                .set(field(Household::getAdmin), member.getId())
                .create();

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
        var household = Instancio.of(Household.class)
                .set(field(Household::getAdmin), member.getId())
                .create();
        var expectedValidUntil = LocalDate.now().plusDays(7);

        // when
        var result = service.setupHousehold(new HouseholdSetup(household, member));

        // then
        assertThat(inviteRepository.findAll())
                .anyMatch(invite -> invite.token().equals(result.getInviteToken())
                        && invite.validUntil().equals(expectedValidUntil));
    }
}
