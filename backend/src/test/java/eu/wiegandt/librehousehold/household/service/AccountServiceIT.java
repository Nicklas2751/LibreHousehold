package eu.wiegandt.librehousehold.household.service;

import eu.wiegandt.librehousehold.TestcontainersConfiguration;
import eu.wiegandt.librehousehold.household.exception.EmailNotVerifiedException;
import eu.wiegandt.librehousehold.household.exception.InvalidPasswordException;
import eu.wiegandt.librehousehold.household.model.AccountEntity;
import eu.wiegandt.librehousehold.household.model.AccountIdentityEntity;
import eu.wiegandt.librehousehold.household.model.HouseholdEntity;
import eu.wiegandt.librehousehold.household.model.MemberEntity;
import eu.wiegandt.librehousehold.household.repository.AccountIdentityRepository;
import eu.wiegandt.librehousehold.household.repository.AccountRepository;
import eu.wiegandt.librehousehold.household.repository.HouseholdRepository;
import eu.wiegandt.librehousehold.household.repository.MemberRepository;
import org.instancio.Instancio;
import org.instancio.junit.InstancioExtension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.instancio.Select.field;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {"librehousehold.security.oauth2-client.client-secret=test-client-secret"})
@Import(TestcontainersConfiguration.class)
@ExtendWith(InstancioExtension.class)
class AccountServiceIT {

    @Autowired
    private AccountService accountService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private AccountIdentityRepository accountIdentityRepository;

    @Autowired
    private HouseholdRepository householdRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private UUID createdHouseholdId;

    @AfterEach
    void tearDown() {
        if (createdHouseholdId != null) {
            memberRepository.deleteByHouseholdId(createdHouseholdId);
            householdRepository.deleteById(createdHouseholdId);
        }
    }

    @Test
    void createAccount_validPassword_persistsHashedNotPlaintextPassword() {
        // given
        var household = householdRepository.save(Instancio.create(HouseholdEntity.class));
        createdHouseholdId = household.id();
        var member = memberRepository.save(Instancio.of(MemberEntity.class)
                .set(field(MemberEntity::householdId), household.id())
                .create());
        var rawPassword = "correct horse battery staple";

        // when
        accountService.createAccount(member.getId(), rawPassword);

        // then
        assertThat(accountRepository.findById(member.getId()))
                .hasValueSatisfying(account -> {
                    assertThat(account.passwordHash()).startsWith("$argon2id$");
                    assertThat(passwordEncoder.matches(rawPassword, account.passwordHash())).isTrue();
                });
    }

    @Test
    void createAccount_validPassword_persistsAccountAsUnverified() {
        // given
        var household = householdRepository.save(Instancio.create(HouseholdEntity.class));
        createdHouseholdId = household.id();
        var member = memberRepository.save(Instancio.of(MemberEntity.class)
                .set(field(MemberEntity::householdId), household.id())
                .create());

        // when
        accountService.createAccount(member.getId(), "correct horse battery staple");

        // then
        assertThat(accountRepository.findById(member.getId()))
                .hasValueSatisfying(account -> assertThat(account.emailVerified()).isFalse());
    }

    @Test
    void changePassword_verifiedAccountCorrectOldPassword_persistsNewHashedPassword() {
        // given
        var household = householdRepository.save(Instancio.create(HouseholdEntity.class));
        createdHouseholdId = household.id();
        var member = memberRepository.save(Instancio.of(MemberEntity.class)
                .set(field(MemberEntity::householdId), household.id())
                .create());
        var oldPassword = "correct horse battery staple";
        var newPassword = "new correct horse battery staple";
        accountService.createAccount(member.getId(), oldPassword);
        accountRepository.markEmailVerified(member.getId());

        // when
        accountService.changePassword(member.getId(), oldPassword, newPassword);

        // then
        assertThat(accountRepository.findById(member.getId()))
                .hasValueSatisfying(account -> assertThat(passwordEncoder.matches(newPassword, account.passwordHash())).isTrue());
    }

    @Test
    void resetPassword_validCall_persistsNewHashedPassword() {
        // given
        var household = householdRepository.save(Instancio.create(HouseholdEntity.class));
        createdHouseholdId = household.id();
        var member = memberRepository.save(Instancio.of(MemberEntity.class)
                .set(field(MemberEntity::householdId), household.id())
                .create());
        var newPassword = "correct horse battery staple";
        accountService.createAccount(member.getId(), "old password");

        // when
        accountService.resetPassword(member.getId(), newPassword);

        // then
        assertThat(accountRepository.findById(member.getId()))
                .hasValueSatisfying(account -> assertThat(passwordEncoder.matches(newPassword, account.passwordHash())).isTrue());
    }

    @Test
    void changePassword_unverifiedAccount_throwsEmailNotVerifiedExceptionWithoutUpdating() {
        // given
        var household = householdRepository.save(Instancio.create(HouseholdEntity.class));
        createdHouseholdId = household.id();
        var member = memberRepository.save(Instancio.of(MemberEntity.class)
                .set(field(MemberEntity::householdId), household.id())
                .create());
        var oldPassword = "correct horse battery staple";
        accountService.createAccount(member.getId(), oldPassword);

        // when / then
        assertThatThrownBy(() -> accountService.changePassword(member.getId(), oldPassword, "new correct horse battery staple"))
                .isInstanceOf(EmailNotVerifiedException.class);
    }

    @Test
    void changePassword_federatedAccountWithoutPassword_throwsInvalidPasswordException() {
        // given
        var household = householdRepository.save(Instancio.create(HouseholdEntity.class));
        createdHouseholdId = household.id();
        var member = memberRepository.save(Instancio.of(MemberEntity.class)
                .set(field(MemberEntity::householdId), household.id())
                .create());
        accountService.createFederatedAccount(member.getId());

        // when / then
        assertThatThrownBy(() -> accountService.changePassword(member.getId(), "any old password", "new correct horse battery staple"))
                .isInstanceOf(InvalidPasswordException.class);
    }

    @Test
    void createFederatedAccount_validCall_persistsAccountWithNullPasswordHashAndVerifiedEmail() {
        // given
        var household = householdRepository.save(Instancio.create(HouseholdEntity.class));
        createdHouseholdId = household.id();
        var member = memberRepository.save(Instancio.of(MemberEntity.class)
                .set(field(MemberEntity::householdId), household.id())
                .create());
        var expected = new AccountEntity(member.getId(), null, true, null, null, 0, null);

        // when
        accountService.createFederatedAccount(member.getId());

        // then
        assertThat(accountRepository.findById(member.getId())).get()
                .usingRecursiveComparison().ignoringFields("registeredAt")
                .isEqualTo(expected);
    }

    @Test
    void linkExternalIdentity_validCall_persistsAccountIdentityRow() {
        // given
        var household = householdRepository.save(Instancio.create(HouseholdEntity.class));
        createdHouseholdId = household.id();
        var member = memberRepository.save(Instancio.of(MemberEntity.class)
                .set(field(MemberEntity::householdId), household.id())
                .create());
        var externalSubject = UUID.randomUUID().toString();
        accountService.createFederatedAccount(member.getId());
        var expected = new AccountIdentityEntity(null, member.getId(), "dev-provider", externalSubject, null);

        // when
        accountService.linkExternalIdentity(member.getId(), "dev-provider", externalSubject);

        // then
        assertThat(accountIdentityRepository.findByProviderIdAndExternalSubject("dev-provider", externalSubject)).get()
                .usingRecursiveComparison().ignoringFields("id", "linkedAt")
                .isEqualTo(expected);
    }

    @Test
    void linkExternalIdentity_alreadyLinkedToAnotherMember_throwsDataIntegrityViolationException() {
        // given
        var household = householdRepository.save(Instancio.create(HouseholdEntity.class));
        createdHouseholdId = household.id();
        var linkedMember = memberRepository.save(Instancio.of(MemberEntity.class)
                .set(field(MemberEntity::householdId), household.id())
                .create());
        var otherMember = memberRepository.save(Instancio.of(MemberEntity.class)
                .set(field(MemberEntity::householdId), household.id())
                .create());
        var externalSubject = UUID.randomUUID().toString();
        accountService.linkExternalIdentity(linkedMember.getId(), "dev-provider", externalSubject);

        // when / then
        assertThatThrownBy(() -> accountService.linkExternalIdentity(otherMember.getId(), "dev-provider", externalSubject))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void findMemberIdByExternalIdentity_linkedIdentity_returnsMemberId() {
        // given
        var household = householdRepository.save(Instancio.create(HouseholdEntity.class));
        createdHouseholdId = household.id();
        var member = memberRepository.save(Instancio.of(MemberEntity.class)
                .set(field(MemberEntity::householdId), household.id())
                .create());
        var externalSubject = UUID.randomUUID().toString();
        accountService.linkExternalIdentity(member.getId(), "dev-provider", externalSubject);

        // when
        var result = accountService.findMemberIdByExternalIdentity("dev-provider", externalSubject);

        // then
        assertThat(result).contains(member.getId());
    }

    @Test
    void findMemberIdByExternalIdentity_unlinkedIdentity_returnsEmpty() {
        // when
        var result = accountService.findMemberIdByExternalIdentity("dev-provider", UUID.randomUUID().toString());

        // then
        assertThat(result).isEmpty();
    }
}
