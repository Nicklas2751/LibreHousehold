package eu.wiegandt.librehousehold.household.service;

import eu.wiegandt.librehousehold.household.AccountPrincipal;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.UUID;

/**
 * Ends all sessions of members, e.g. after a password reset (RESET1 in Arc42 Chapter 8). Expired
 * sessions are rejected by the {@code ConcurrentSessionFilter} on their next request. The only
 * place that looks up sessions in the {@link SessionRegistry}, so the services need to know
 * neither the registry nor how it identifies a member.
 */
@Component
public class MemberSessions {

    private final SessionRegistry sessionRegistry;

    public MemberSessions(SessionRegistry sessionRegistry) {
        this.sessionRegistry = sessionRegistry;
    }

    public void expireAll(UUID memberId) {
        sessionRegistry.getAllSessions(AccountPrincipal.identifiedBy(memberId), false)
                .forEach(SessionInformation::expireNow);
    }

    public void expireAll(Collection<UUID> memberIds) {
        memberIds.forEach(this::expireAll);
    }
}
