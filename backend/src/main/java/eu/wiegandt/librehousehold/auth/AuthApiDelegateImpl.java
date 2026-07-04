package eu.wiegandt.librehousehold.auth;

import eu.wiegandt.librehousehold.api.AuthApiDelegate;
import eu.wiegandt.librehousehold.auth.model.AccountEntity;
import eu.wiegandt.librehousehold.auth.repository.AccountRepository;
import eu.wiegandt.librehousehold.household.HouseholdSetupPort;
import eu.wiegandt.librehousehold.model.AuthProviders;
import eu.wiegandt.librehousehold.model.HouseholdSetupResponse;
import eu.wiegandt.librehousehold.model.LocalRegistration;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.FactorGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.UUID;

@Component
public class AuthApiDelegateImpl implements AuthApiDelegate {

    private final AuthProviderService authProviderService;
    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final HouseholdSetupPort householdSetupPort;
    private final UserDetailsService userDetailsService;
    private final HttpServletRequest request;
    private final HttpServletResponse httpServletResponse;

    AuthApiDelegateImpl(AuthProviderService authProviderService,
                        AccountRepository accountRepository,
                        PasswordEncoder passwordEncoder,
                        HouseholdSetupPort householdSetupPort,
                        UserDetailsService userDetailsService,
                        HttpServletRequest request,
                        HttpServletResponse httpServletResponse) {
        this.authProviderService = authProviderService;
        this.accountRepository = accountRepository;
        this.passwordEncoder = passwordEncoder;
        this.householdSetupPort = householdSetupPort;
        this.userDetailsService = userDetailsService;
        this.request = request;
        this.httpServletResponse = httpServletResponse;
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
        var setupResponse = householdSetupPort.setupForLocalRegistration(
                accountId,
                registration.getHouseholdName(),
                registration.getHouseholdImage().orElse(null),
                registration.getMemberName(),
                registration.getMemberAvatar().orElse(null));
        establishSession(registration.getEmail());
        return ResponseEntity.ok(setupResponse);
    }

    private void establishSession(String email) {
        var userDetails = userDetailsService.loadUserByUsername(email);
        var authorities = new ArrayList<GrantedAuthority>(userDetails.getAuthorities());
        // Spring AS 7.1 reads auth_time from FactorGrantedAuthority.getIssuedAt()
        authorities.add(FactorGrantedAuthority.fromAuthority(FactorGrantedAuthority.PASSWORD_AUTHORITY));
        var authentication = UsernamePasswordAuthenticationToken.authenticated(
                userDetails, null, authorities);
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        new HttpSessionSecurityContextRepository().saveContext(context, request, httpServletResponse);
    }
}
