package eu.wiegandt.librehousehold.auth;

import eu.wiegandt.librehousehold.auth.repository.AccountRepository;
import eu.wiegandt.librehousehold.household.MemberEmailChanged;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Component
class AccountEmailListener {

    private final AccountRepository accountRepository;

    AccountEmailListener(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @ApplicationModuleListener
    void on(MemberEmailChanged event) {
        accountRepository.updateEmail(event.memberId(), event.newEmail());
    }
}
