package eu.wiegandt.librehousehold.auth;

import eu.wiegandt.librehousehold.api.AuthApiDelegate;
import eu.wiegandt.librehousehold.model.AuthProviders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Component
public class AuthApiDelegateImpl implements AuthApiDelegate {

    private final AuthProviderService authProviderService;

    AuthApiDelegateImpl(AuthProviderService authProviderService) {
        this.authProviderService = authProviderService;
    }

    @Override
    public ResponseEntity<AuthProviders> getAuthProviders() {
        return ResponseEntity.ok(authProviderService.getAvailableProviders());
    }
}
