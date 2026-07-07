package eu.wiegandt.librehousehold.auth;

import eu.wiegandt.librehousehold.api.AuthApiDelegate;
import eu.wiegandt.librehousehold.core.AccountRegistration;
import eu.wiegandt.librehousehold.core.SessionEstablishment;
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
    private final AccountRegistration accountRegistration;
    private final PasswordEncoder passwordEncoder;
    private final HouseholdSetupPort householdSetupPort;
    private final SessionEstablishment sessionEstablishment;

    AuthApiDelegateImpl(AuthProviderService authProviderService,
                        AccountRegistration accountRegistration,
                        PasswordEncoder passwordEncoder,
                        HouseholdSetupPort householdSetupPort,
                        SessionEstablishment sessionEstablishment) {
        this.authProviderService = authProviderService;
        this.accountRegistration = accountRegistration;
        this.passwordEncoder = passwordEncoder;
        this.householdSetupPort = householdSetupPort;
        this.sessionEstablishment = sessionEstablishment;
    }

    @Override
    public ResponseEntity<AuthProviders> getAuthProviders() {
        return ResponseEntity.ok(authProviderService.getAvailableProviders());
    }

    @Override
    @Transactional
    public ResponseEntity<HouseholdSetupResponse> registerLocal(LocalRegistration registration) {
        var accountId = UUID.randomUUID();
        var encodedPassword = passwordEncoder.encode(registration.getPassword());
        accountRegistration.registerLocalAccount(accountId, registration.getEmail(), encodedPassword);
        var setupResponse = householdSetupPort.setupForLocalRegistration(
                accountId,
                registration.getHouseholdName(),
                registration.getHouseholdImage().orElse(null),
                registration.getMemberName(),
                registration.getMemberAvatar().orElse(null));
        sessionEstablishment.establishSession(registration.getEmail());
        return ResponseEntity.ok(setupResponse);
    }
}
