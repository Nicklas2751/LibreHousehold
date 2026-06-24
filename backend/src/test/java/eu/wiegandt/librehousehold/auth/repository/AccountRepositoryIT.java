package eu.wiegandt.librehousehold.auth.repository;

import eu.wiegandt.librehousehold.TestcontainersConfiguration;
import eu.wiegandt.librehousehold.auth.model.AccountEntity;
import eu.wiegandt.librehousehold.auth.model.FederatedIdentityEntity;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import(TestcontainersConfiguration.class)
class AccountRepositoryIT {

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private FederatedIdentityRepository federatedIdentityRepository;

    @Nested
    class FindByEmail {

        @Test
        void unknownEmail_returnsEmpty() {
            // given / when
            var result = accountRepository.findByEmail("nobody@example.com");

            // then
            assertThat(result).isEmpty();
        }

        @Test
        void existingEmail_returnsAccount() {
            // given
            var account = new AccountEntity(UUID.randomUUID(), "find-by-email@example.com", null);
            accountRepository.save(account);

            // when
            var result = accountRepository.findByEmail("find-by-email@example.com");

            // then
            assertThat(result).isPresent();
            assertThat(result.get().email()).isEqualTo("find-by-email@example.com");
        }
    }

    @Nested
    class FindByProviderAndProviderSub {

        @Test
        void existingIdentity_returnsFederatedIdentity() {
            // given
            var account = new AccountEntity(UUID.randomUUID(), "social@example.com", null);
            accountRepository.save(account);
            var identity = new FederatedIdentityEntity(UUID.randomUUID(), account.getId(), "google", "google-sub-123");
            federatedIdentityRepository.save(identity);

            // when
            var result = federatedIdentityRepository.findByProviderAndProviderSub("google", "google-sub-123");

            // then
            assertThat(result).isPresent();
            assertThat(result.get().accountId()).isEqualTo(account.getId());
            assertThat(result.get().provider()).isEqualTo("google");
        }

        @Test
        void unknownProviderSub_returnsEmpty() {
            // given / when
            var result = federatedIdentityRepository.findByProviderAndProviderSub("google", "nonexistent-sub");

            // then
            assertThat(result).isEmpty();
        }
    }
}
