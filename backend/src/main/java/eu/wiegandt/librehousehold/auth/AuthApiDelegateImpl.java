package eu.wiegandt.librehousehold.auth;

import eu.wiegandt.librehousehold.api.AuthApiDelegate;
import eu.wiegandt.librehousehold.auth.model.AccountEntity;
import eu.wiegandt.librehousehold.auth.repository.AccountRepository;
import eu.wiegandt.librehousehold.household.HouseholdSetupPort;
import eu.wiegandt.librehousehold.model.AuthProviders;
import eu.wiegandt.librehousehold.model.HouseholdSetupResponse;
import eu.wiegandt.librehousehold.model.LocalRegistration;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
public class AuthApiDelegateImpl implements AuthApiDelegate {

    private final AuthProviderService authProviderService;
    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final HouseholdSetupPort householdSetupPort;

    AuthApiDelegateImpl(AuthProviderService authProviderService,
                        AccountRepository accountRepository,
                        PasswordEncoder passwordEncoder,
                        HouseholdSetupPort householdSetupPort) {
        this.authProviderService = authProviderService;
        this.accountRepository = accountRepository;
        this.passwordEncoder = passwordEncoder;
        this.householdSetupPort = householdSetupPort;
    }

    @Override
    public ResponseEntity<AuthProviders> getAuthProviders() {
        return ResponseEntity.ok(authProviderService.getAvailableProviders());
    }

    @Override
    @Transactional
    public ResponseEntity<HouseholdSetupResponse> registerLocal(LocalRegistration registration) {
        var accountId = UUID.randomUUID();
        accountRepository.save(new AccountEntity(
                accountId,
                registration.getEmail(),
                passwordEncoder.encode(registration.getPassword())));
        var response = householdSetupPort.setupForLocalRegistration(
                accountId,
                registration.getHouseholdName(),
                registration.getHouseholdImage().orElse(null),
                registration.getMemberName(),
                registration.getMemberAvatar().orElse(null));
        return ResponseEntity.ok(response);
    }
}
