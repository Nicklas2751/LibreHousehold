package eu.wiegandt.librehousehold.auth;

import eu.wiegandt.librehousehold.TestcontainersConfiguration;
import eu.wiegandt.librehousehold.auth.model.AccountEntity;
import eu.wiegandt.librehousehold.auth.repository.AccountRepository;
import eu.wiegandt.librehousehold.household.model.HouseholdEntity;
import eu.wiegandt.librehousehold.household.model.MemberEntity;
import eu.wiegandt.librehousehold.household.repository.HouseholdRepository;
import eu.wiegandt.librehousehold.household.repository.MemberRepository;
import eu.wiegandt.librehousehold.household.service.MemberManagementService;
import eu.wiegandt.librehousehold.model.MemberUpdate;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import(TestcontainersConfiguration.class)
class EmailUpdateIT {

    @Autowired
    MemberManagementService memberManagementService;

    @Autowired
    AccountRepository accountRepository;

    @Autowired
    MemberRepository memberRepository;

    @Autowired
    HouseholdRepository householdRepository;

    @Nested
    class updateMember {

        @Test
        void emailInUpdate_updatesEmailInAuthAccount() {
            // given
            var sharedId = UUID.randomUUID();
            var householdId = UUID.randomUUID();
            householdRepository.save(new HouseholdEntity(householdId, "Test Haushalt", null));
            memberRepository.save(new MemberEntity(sharedId, "Test User", null, householdId, false));
            accountRepository.save(new AccountEntity(sharedId, "old@example.com", null));
            var update = new MemberUpdate().email("new@example.com");

            // when
            memberManagementService.updateMember(sharedId, update);

            // then
            assertThat(accountRepository.findByEmail("new@example.com")).isPresent();
            assertThat(accountRepository.findByEmail("old@example.com")).isEmpty();
        }
    }
}
