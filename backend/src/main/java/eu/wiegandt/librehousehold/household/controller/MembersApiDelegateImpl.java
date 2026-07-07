package eu.wiegandt.librehousehold.household.controller;

import eu.wiegandt.librehousehold.api.MembersApiDelegate;
import eu.wiegandt.librehousehold.core.CurrentUserIdProvider;
import eu.wiegandt.librehousehold.core.SessionEstablishment;
import eu.wiegandt.librehousehold.household.service.MemberManagementService;
import eu.wiegandt.librehousehold.model.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@Component
public class MembersApiDelegateImpl implements MembersApiDelegate {

    private final MemberManagementService memberManagementService;
    private final SessionEstablishment sessionEstablishment;
    private final CurrentUserIdProvider currentUserIdProvider;

    public MembersApiDelegateImpl(MemberManagementService memberManagementService,
                                  SessionEstablishment sessionEstablishment,
                                  CurrentUserIdProvider currentUserIdProvider) {
        this.memberManagementService = memberManagementService;
        this.sessionEstablishment = sessionEstablishment;
        this.currentUserIdProvider = currentUserIdProvider;
    }

    @Override
    @PreAuthorize("@householdScopeChecker.isCurrentUserInHousehold(#householdId)")
    public ResponseEntity<List<Member>> getMembers(UUID householdId) {
        return ResponseEntity.ok(memberManagementService.getMembers(householdId));
    }

    @Override
    @PreAuthorize("@householdScopeChecker.isCurrentUserInHousehold(#householdId)")
    public ResponseEntity<Member> getMember(UUID householdId, UUID memberId) {
        return ResponseEntity.ok(memberManagementService.getMember(memberId));
    }

    @Override
    public ResponseEntity<InviteInfo> resolveInvite(UUID token) {
        return ResponseEntity.ok(memberManagementService.resolveInvite(token));
    }

    @Override
    public ResponseEntity<Member> joinHousehold(UUID token, LocalMemberRegistration localMemberRegistration) {
        var member = memberManagementService.joinHouseholdLocal(token, localMemberRegistration);
        sessionEstablishment.establishSession(localMemberRegistration.getEmail());
        return ResponseEntity.created(URI.create("/household/" + member.getId() + "/members/" + member.getId()))
                .body(member);
    }

    @Override
    public ResponseEntity<Member> joinHouseholdAuthenticated(HouseholdJoin householdJoin) {
        var accountId = currentUserIdProvider.getCurrentUserId();
        var member = memberManagementService.joinHouseholdAuthenticated(
                accountId, householdJoin.getToken(), householdJoin.getMemberName(),
                householdJoin.getMemberAvatar().orElse(null));
        return ResponseEntity.status(HttpStatus.CREATED).body(member);
    }

    @Override
    @PreAuthorize("@householdScopeChecker.isCurrentUserInHousehold(#householdId) and @authorChecker.isAuthor(#resourceId)")
    public ResponseEntity<Void> updateMember(UUID householdId, UUID resourceId, MemberUpdate memberUpdate) {
        memberManagementService.updateMember(resourceId, memberUpdate);
        return ResponseEntity.noContent().build();
    }

    @Override
    @PreAuthorize("@householdScopeChecker.isCurrentUserInHousehold(#householdId) and @adminChecker.isAdmin()")
    public ResponseEntity<Void> removeMember(UUID householdId, UUID memberId) {
        memberManagementService.removeMember(memberId);
        return ResponseEntity.noContent().build();
    }
}
