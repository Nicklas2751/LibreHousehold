package eu.wiegandt.librehousehold.household;

import eu.wiegandt.librehousehold.auth.ResourceOwnerQuery;
import eu.wiegandt.librehousehold.household.repository.MemberRepository;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
class MemberOwnerQuery implements ResourceOwnerQuery {

    private final MemberRepository memberRepository;

    MemberOwnerQuery(MemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    @Override
    public boolean isOwner(UUID resourceId, UUID accountId) {
        return resourceId.equals(accountId) && memberRepository.existsById(resourceId);
    }
}
