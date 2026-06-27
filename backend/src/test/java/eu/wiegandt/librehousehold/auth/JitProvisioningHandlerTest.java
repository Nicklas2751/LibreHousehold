package eu.wiegandt.librehousehold.auth;

import eu.wiegandt.librehousehold.auth.model.AccountEntity;
import eu.wiegandt.librehousehold.auth.model.FederatedIdentityEntity;
import eu.wiegandt.librehousehold.auth.repository.AccountRepository;
import eu.wiegandt.librehousehold.auth.repository.FederatedIdentityRepository;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class JitProvisioningHandlerTest {

    @InjectMocks
    JitProvisioningHandler handler;

    @Mock
    AccountRepository accountRepository;

    @Mock
    FederatedIdentityRepository federatedIdentityRepository;

    @Nested
    class provision {

        @Test
        void knownProviderSub_doesNotInsertAccount() {
            // given
            var accountId = UUID.randomUUID();
            var existingIdentity = new FederatedIdentityEntity(UUID.randomUUID(), accountId, "google", "sub-123");
            var existingAccount = new AccountEntity(accountId, "user@gmail.com", null);
            doReturn(Optional.of(existingIdentity)).when(federatedIdentityRepository)
                    .findByProviderAndProviderSub("google", "sub-123");
            doReturn(Optional.of(existingAccount)).when(accountRepository).findById(accountId);

            // when
            handler.provision("google", "sub-123", "user@gmail.com");

            // then
            verify(accountRepository, never()).save(any());
            verify(federatedIdentityRepository, never()).save(any());
        }

        @Test
        void knownProviderSub_emailChanged_updatesAccountEmail() {
            // given
            var accountId = UUID.randomUUID();
            var existingIdentity = new FederatedIdentityEntity(UUID.randomUUID(), accountId, "google", "sub-123");
            var existingAccount = new AccountEntity(accountId, "old@gmail.com", null);
            doReturn(Optional.of(existingIdentity)).when(federatedIdentityRepository)
                    .findByProviderAndProviderSub("google", "sub-123");
            doReturn(Optional.of(existingAccount)).when(accountRepository).findById(accountId);

            // when
            handler.provision("google", "sub-123", "new@gmail.com");

            // then
            verify(accountRepository).updateEmail(accountId, "new@gmail.com");
        }

        @Test
        void unknownProviderSub_newEmail_createsAccountAndFederatedIdentity() {
            // given
            doReturn(Optional.empty()).when(federatedIdentityRepository)
                    .findByProviderAndProviderSub("google", "new-sub");
            doReturn(Optional.empty()).when(accountRepository).findByEmail("brand-new@gmail.com");

            // when
            handler.provision("google", "new-sub", "brand-new@gmail.com");

            // then
            var accountCaptor = ArgumentCaptor.forClass(AccountEntity.class);
            verify(accountRepository).save(accountCaptor.capture());
            assertThat(accountCaptor.getValue().email()).isEqualTo("brand-new@gmail.com");
            assertThat(accountCaptor.getValue().passwordHash()).isNull();

            var identityCaptor = ArgumentCaptor.forClass(FederatedIdentityEntity.class);
            verify(federatedIdentityRepository).save(identityCaptor.capture());
            assertThat(identityCaptor.getValue().provider()).isEqualTo("google");
            assertThat(identityCaptor.getValue().providerSub()).isEqualTo("new-sub");
            assertThat(identityCaptor.getValue().accountId()).isEqualTo(accountCaptor.getValue().id());
        }

        @Test
        void unknownProviderSub_existingEmail_throwsOAuth2AuthenticationException() {
            // given
            var conflictingAccount = new AccountEntity(UUID.randomUUID(), "taken@example.com", "hash");
            doReturn(Optional.empty()).when(federatedIdentityRepository)
                    .findByProviderAndProviderSub("google", "sub-conflict");
            doReturn(Optional.of(conflictingAccount)).when(accountRepository).findByEmail("taken@example.com");

            // when / then
            assertThatThrownBy(() -> handler.provision("google", "sub-conflict", "taken@example.com"))
                    .isInstanceOf(OAuth2AuthenticationException.class)
                    .extracting(ex -> ((OAuth2AuthenticationException) ex).getError().getErrorCode())
                    .isEqualTo("email_already_registered");
        }
    }
}
