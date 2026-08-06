package eu.wiegandt.librehousehold.household.service;

import eu.wiegandt.librehousehold.household.AccountPrincipal;
import eu.wiegandt.librehousehold.household.repository.AccountRepository;
import org.jspecify.annotations.NonNull;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class AccountUserDetailsService implements UserDetailsService {

    private final MemberManagementService memberManagementService;
    private final AccountRepository accountRepository;

    public AccountUserDetailsService(MemberManagementService memberManagementService, AccountRepository accountRepository) {
        this.memberManagementService = memberManagementService;
        this.accountRepository = accountRepository;
    }

    @Override
    public UserDetails loadUserByUsername(@NonNull String email) {
        var memberId = memberManagementService.findMemberIdByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException(email));
        var account = accountRepository.findById(memberId)
                .orElseThrow(() -> new UsernameNotFoundException(email));
        return new AccountPrincipal(email, account.passwordHash());
    }
}
