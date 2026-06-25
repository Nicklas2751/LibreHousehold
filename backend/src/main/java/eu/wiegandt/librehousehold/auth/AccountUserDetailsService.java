package eu.wiegandt.librehousehold.auth;

import eu.wiegandt.librehousehold.auth.repository.AccountRepository;
import org.jspecify.annotations.NonNull;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;

@Component
class AccountUserDetailsService implements UserDetailsService {

    private final AccountRepository accountRepository;

    AccountUserDetailsService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @Override
    @NonNull
    public UserDetails loadUserByUsername(@NonNull String email) {
        var account = accountRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException(email));
        // username = accountId so Spring AS sets sub = UUID; password verification uses PasswordEncoder
        return User.withUsername(account.id().toString())
                .password(account.passwordHash())
                .build();
    }
}
