package eu.wiegandt.librehousehold.household;

import eu.wiegandt.librehousehold.household.repository.MemberRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Household/role-scoped access control, referenced from {@code @PreAuthorize} SpEL expressions on
 * the various {@code *ApiDelegateImpl} classes (see DD-8). Lives in the {@code household} root
 * package, not {@code core}: {@code core} must not depend on any other module (see the "Module
 * Dependency Direction" rule in Arc42 Chapter 5), and this guard inherently needs household/member
 * data. Other modules (e.g. {@code tasks}, {@code expenses}) depend on this class only through the
 * SpEL bean reference {@code @householdAccessGuard.…}, which Spring resolves at runtime by bean
 * name — a real {@code household} dependency (same direction already established by
 * {@code HouseholdQuery}/{@code MemberQuery}, ADR-011), but one that Spring Modulith's
 * {@code ApplicationModules.verify()} cannot see, since it never becomes a Java compile-time
 * reference.
 *
 * <p>Depends directly on {@link MemberRepository} rather than the {@link MemberQuery} named
 * interface: the "does member X belong to household Y?" check is needed only here, inside the
 * {@code household} module itself, so exposing it on the cross-module {@code MemberQuery} would
 * violate the "Named Interfaces are for real cross-module access" principle (the same correction
 * already applied to {@code MemberManagementService.existsByEmail}/{@code isEmailAvailable} in
 * P1.3).
 */
@Component("householdAccessGuard")
public class HouseholdAccessGuard {

    private final MemberRepository memberRepository;

    public HouseholdAccessGuard(MemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    public boolean isMember(UUID householdId, Authentication authentication) {
        var principal = (AccountOidcPrincipal) authentication.getPrincipal();
        return memberRepository.existsByIdAndHouseholdId(principal.memberId(), householdId);
    }

    public boolean isAdminOfHousehold(UUID householdId, Authentication authentication) {
        var principal = (AccountOidcPrincipal) authentication.getPrincipal();
        return memberRepository.existsByIdAndHouseholdIdAndIsAdminTrue(principal.memberId(), householdId);
    }

    public boolean isSelf(UUID memberId, Authentication authentication) {
        var principal = (AccountOidcPrincipal) authentication.getPrincipal();
        return principal.memberId().equals(memberId);
    }
}
