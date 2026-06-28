package eu.wiegandt.librehousehold.auth;

import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
class MemberOwnerQuery implements ResourceOwnerQuery {

    @Override
    public boolean isOwner(UUID resourceId, UUID accountId) {
        return resourceId.equals(accountId);
    }
}
