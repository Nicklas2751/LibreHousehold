package eu.wiegandt.librehousehold.household.service;

import eu.wiegandt.librehousehold.TestcontainersConfiguration;
import eu.wiegandt.librehousehold.household.AccountPrincipal;
import eu.wiegandt.librehousehold.household.model.HouseholdEntity;
import eu.wiegandt.librehousehold.household.model.MemberEntity;
import eu.wiegandt.librehousehold.household.repository.AccountRepository;
import eu.wiegandt.librehousehold.household.repository.HouseholdRepository;
import eu.wiegandt.librehousehold.household.repository.MemberRepository;
import org.instancio.Instancio;
import org.instancio.junit.InstancioExtension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.instancio.Select.field;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {"librehousehold.security.oauth2-client.client-secret=test-client-secret"})
@Import(TestcontainersConfiguration.class)
@ExtendWith(InstancioExtension.class)
class AccountUserDetailsServiceIT {

    @Autowired
    private AccountUserDetailsService accountUserDetailsService;

    @Autowired
    private AccountService accountService;

    @Autowired
    private HouseholdRepository householdRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private AccountRepository accountRepository;

    private UUID createdHouseholdId;

    @AfterEach
    void tearDown() {
        if (createdHouseholdId != null) {
            memberRepository.deleteByHouseholdId(createdHouseholdId);
            householdRepository.deleteById(createdHouseholdId);
        }
    }

    @Nested
    class loadUserByUsername {

        @Test
        void unknownEmail_throwsUsernameNotFoundException() {
            // given
            var email = "unknown-" + UUID.randomUUID() + "@example.com";

            // when / then
            assertThatThrownBy(() -> accountUserDetailsService.loadUserByUsername(email))
                    .isInstanceOf(UsernameNotFoundException.class);
        }

        @Test
        void memberExistsWithoutAccount_throwsUsernameNotFoundException() {
            // given
            var household = householdRepository.save(Instancio.create(HouseholdEntity.class));
            createdHouseholdId = household.id();
            var member = memberRepository.save(Instancio.of(MemberEntity.class)
                    .set(field(MemberEntity::householdId), household.id())
                    .create());

            // when / then
            assertThatThrownBy(() -> accountUserDetailsService.loadUserByUsername(member.email()))
                    .isInstanceOf(UsernameNotFoundException.class);
        }

        @Test
        void memberAndAccountExist_returnsMatchingAccountPrincipal() {
            // given
            var household = householdRepository.save(Instancio.create(HouseholdEntity.class));
            createdHouseholdId = household.id();
            var member = memberRepository.save(Instancio.of(MemberEntity.class)
                    .set(field(MemberEntity::householdId), household.id())
                    .create());
            var rawPassword = "correct horse battery staple";
            accountService.createAccount(member.getId(), rawPassword);
            var passwordHash = accountRepository.findById(member.getId()).orElseThrow().passwordHash();
            var expectedPrincipal = new AccountPrincipal(member.email(), passwordHash);

            // when
            var result = accountUserDetailsService.loadUserByUsername(member.email());

            // then
            assertThat(result).usingRecursiveComparison().isEqualTo(expectedPrincipal);
        }
    }
}
