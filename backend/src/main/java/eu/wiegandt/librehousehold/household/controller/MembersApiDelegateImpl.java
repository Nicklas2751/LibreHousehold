package eu.wiegandt.librehousehold.household.controller;

import eu.wiegandt.librehousehold.api.MembersApiDelegate;
import eu.wiegandt.librehousehold.household.service.MemberManagementService;
import eu.wiegandt.librehousehold.model.EmailAvailability;
import eu.wiegandt.librehousehold.model.InviteInfo;
import eu.wiegandt.librehousehold.model.Member;
import eu.wiegandt.librehousehold.model.MemberRegistration;
import eu.wiegandt.librehousehold.model.MemberUpdate;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
    public ResponseEntity<EmailAvailability> checkEmailAvailability(String email) {
        return ResponseEntity.ok(new EmailAvailability(memberManagementService.isEmailAvailable(email)));
    }

    @Override
    @PreAuthorize("@householdAccessGuard.isMember(#householdId, authentication)")
    public ResponseEntity<List<Member>> getMembers(UUID householdId) {
        return ResponseEntity.ok(memberManagementService.getMembers(householdId));
    }

    @Override
    @PreAuthorize("@householdAccessGuard.isMember(#householdId, authentication)")
    public ResponseEntity<Member> getMember(UUID householdId, UUID memberId) {
        return ResponseEntity.ok(memberManagementService.getMember(householdId, memberId));
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
    @PreAuthorize("@householdAccessGuard.isSelf(#memberId, authentication) "
            + "or @householdAccessGuard.isAdminOfHousehold(#householdId, authentication)")
    public ResponseEntity<Void> updateMember(UUID householdId, UUID memberId, MemberUpdate memberUpdate) {
        memberManagementService.updateMember(householdId, memberId, memberUpdate);
        return ResponseEntity.noContent().build();
    }

    @Override
    @PreAuthorize("@householdAccessGuard.isAdminOfHousehold(#householdId, authentication)")
    public ResponseEntity<Void> removeMember(UUID householdId, UUID memberId) {
        memberManagementService.removeMember(householdId, memberId);
        return ResponseEntity.noContent().build();
    }
}
