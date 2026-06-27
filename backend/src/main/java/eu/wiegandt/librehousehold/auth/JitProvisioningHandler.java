package eu.wiegandt.librehousehold.auth;

import eu.wiegandt.librehousehold.auth.model.AccountEntity;
import eu.wiegandt.librehousehold.auth.model.FederatedIdentityEntity;
import eu.wiegandt.librehousehold.auth.repository.AccountRepository;
import eu.wiegandt.librehousehold.auth.repository.FederatedIdentityRepository;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
class JitProvisioningHandler {

    private final AccountRepository accountRepository;
    private final FederatedIdentityRepository federatedIdentityRepository;

    JitProvisioningHandler(AccountRepository accountRepository,
                           FederatedIdentityRepository federatedIdentityRepository) {
        this.accountRepository = accountRepository;
        this.federatedIdentityRepository = federatedIdentityRepository;
    }

    /**
     * Provisions a federated identity on first login and keeps the stored email in sync with
     * the identity provider. Throws if another local account already owns the email address.
     */
    @Transactional
    void provision(String provider, String providerSub, String email) {
        var existingIdentity = federatedIdentityRepository.findByProviderAndProviderSub(provider, providerSub);
        if (existingIdentity.isPresent()) {
            // If the user changed their email at the provider, update our stored copy
            accountRepository.findById(existingIdentity.get().accountId())
                    .filter(account -> !account.email().equals(email))
                    .ifPresent(account -> accountRepository.updateEmail(account.id(), email));
            return;
        }
        if (accountRepository.findByEmail(email).isPresent()) {
            throw new OAuth2AuthenticationException(new OAuth2Error("email_already_registered"));
        }
        var accountId = UUID.randomUUID();
        accountRepository.save(new AccountEntity(accountId, email, null));
        federatedIdentityRepository.save(new FederatedIdentityEntity(UUID.randomUUID(), accountId, provider, providerSub));
    }
}
