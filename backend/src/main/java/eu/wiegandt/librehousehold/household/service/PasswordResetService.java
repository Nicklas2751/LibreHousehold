package eu.wiegandt.librehousehold.household.service;

import eu.wiegandt.librehousehold.household.AccountPrincipal;
import eu.wiegandt.librehousehold.household.PasswordReset;
import eu.wiegandt.librehousehold.household.exception.AccountTokenInvalidException;
import eu.wiegandt.librehousehold.household.exception.MemberNotFoundException;
import eu.wiegandt.librehousehold.household.exception.PasswordResetTokenInvalidException;
import eu.wiegandt.librehousehold.household.repository.AccountRepository;
import eu.wiegandt.librehousehold.household.repository.MemberRepository;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Orchestrates the password-reset confirmation flow: resets the password, then invalidates all of
 * the account's existing sessions and revokes its cached OAuth2 authorized client (see RESET1 in
 * Arc42 Chapter 8). Kept separate from {@link AccountService} on purpose (SRP): {@code AccountService}
 * stays free of session/OAuth2 security-infrastructure concerns.
 */
@Service
public class PasswordResetService implements PasswordReset {

    // Duplicated from RegisteredClientSeeder.CLIENT_ID (config module) on purpose: household must
    // not depend on any other business module (see "Module Dependency Direction" in Arc42 Chapter 5).
    // Same pattern already used by HouseholdAccessGuardTest.
    private static final String CLIENT_REGISTRATION_ID = "spa-backend-client";

    private final MemberManagementService memberManagementService;
    private final AccountTokenService accountTokenService;
    private final AccountService accountService;
    private final MemberRepository memberRepository;
    private final AccountRepository accountRepository;
    private final SessionRegistry sessionRegistry;
    private final OAuth2AuthorizedClientService authorizedClientService;

    public PasswordResetService(MemberManagementService memberManagementService,
                                 AccountTokenService accountTokenService,
                                 AccountService accountService,
                                 MemberRepository memberRepository,
                                 AccountRepository accountRepository,
                                 SessionRegistry sessionRegistry,
                                 OAuth2AuthorizedClientService authorizedClientService) {
        this.memberManagementService = memberManagementService;
        this.accountTokenService = accountTokenService;
        this.accountService = accountService;
        this.memberRepository = memberRepository;
        this.accountRepository = accountRepository;
        this.sessionRegistry = sessionRegistry;
        this.authorizedClientService = authorizedClientService;
    }

    @Override
    public void requestPasswordReset(String email) {
        memberManagementService.requestPasswordReset(email);
    }

    @Override
    @Transactional
    public void confirmPasswordReset(UUID token, String newPassword) {
        var memberId = consumePasswordResetToken(token);
        var member = memberRepository.findById(memberId).orElseThrow(MemberNotFoundException::new);
        var account = accountRepository.findById(memberId).orElseThrow(MemberNotFoundException::new);
        var principal = new AccountPrincipal(member.email(), account.passwordHash(), account.emailVerified());

        accountService.resetPassword(memberId, newPassword);

        sessionRegistry.getAllSessions(principal, false).forEach(SessionInformation::expireNow);
        authorizedClientService.removeAuthorizedClient(CLIENT_REGISTRATION_ID, member.email());
    }

    private UUID consumePasswordResetToken(UUID token) {
        try {
            return accountTokenService.consumeToken(token, AccountTokenService.PASSWORD_RESET_PURPOSE);
        } catch (AccountTokenInvalidException _) {
            throw new PasswordResetTokenInvalidException();
        }
    }
}
