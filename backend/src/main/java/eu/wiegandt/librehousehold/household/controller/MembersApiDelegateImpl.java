package eu.wiegandt.librehousehold.household.controller;

import eu.wiegandt.librehousehold.api.MembersApiDelegate;
import eu.wiegandt.librehousehold.household.service.MemberManagementService;
import org.springframework.security.access.prepost.PreAuthorize;
import eu.wiegandt.librehousehold.model.InviteInfo;
import eu.wiegandt.librehousehold.model.Member;
import eu.wiegandt.librehousehold.model.MemberRegistration;
import eu.wiegandt.librehousehold.model.MemberUpdate;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@Component
public class MembersApiDelegateImpl implements MembersApiDelegate {

    private final MemberManagementService memberManagementService;

    public MembersApiDelegateImpl(MemberManagementService memberManagementService) {
        this.memberManagementService = memberManagementService;
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
    public ResponseEntity<Member> joinHousehold(UUID token, MemberRegistration memberRegistration) {
        var member = memberManagementService.joinHousehold(token, memberRegistration);
        return ResponseEntity.created(URI.create("/household/" + member.getId() + "/members/" + member.getId()))
                .body(member);
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
