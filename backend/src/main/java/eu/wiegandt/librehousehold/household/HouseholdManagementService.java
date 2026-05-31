package eu.wiegandt.librehousehold.household;

import eu.wiegandt.librehousehold.model.HouseholdUpdate;
import eu.wiegandt.librehousehold.model.InviteResponse;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

@Service
class HouseholdManagementService {

    private static final int INVITE_VALIDITY_DAYS = 7;

    private final HouseholdRepository householdRepository;
    private final MemberRepository memberRepository;
    private final InviteRepository inviteRepository;
    private final ApplicationEventPublisher eventPublisher;

    HouseholdManagementService(HouseholdRepository householdRepository,
                               MemberRepository memberRepository,
                               InviteRepository inviteRepository,
                               ApplicationEventPublisher eventPublisher) {
        this.householdRepository = householdRepository;
        this.memberRepository = memberRepository;
        this.inviteRepository = inviteRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    void updateName(UUID householdId, HouseholdUpdate update) {
        var updatedRows = householdRepository.updateName(householdId, update.getName());
        if (updatedRows == 0) {
            throw new HouseholdNotFoundException();
        }
    }

    @Transactional
    void deleteHousehold(UUID householdId) {
        inviteRepository.deleteByHouseholdId(householdId);
        memberRepository.deleteByHouseholdId(householdId);
        var deletedRows = householdRepository.deleteHouseholdById(householdId);
        if (deletedRows == 0) {
            throw new HouseholdNotFoundException();
        }
        eventPublisher.publishEvent(new HouseholdDeleted(householdId));
    }

    InviteResponse getInvite(UUID householdId) {
        var invite = inviteRepository.findByHouseholdId(householdId)
                .orElseThrow(HouseholdNotFoundException::new);
        return toInviteResponse(invite);
    }

    @Transactional
    InviteResponse regenerateInvite(UUID householdId) {
        inviteRepository.deleteByHouseholdId(householdId);
        try {
            var newInvite = inviteRepository.save(new InviteEntity(
                    null,
                    householdId,
                    UUID.randomUUID(),
                    LocalDate.now().plusDays(INVITE_VALIDITY_DAYS)
            ));
            return toInviteResponse(newInvite);
        } catch (DataIntegrityViolationException e) {
            throw new HouseholdNotFoundException();
        }
    }

    @Transactional
    void transferOwnership(UUID householdId, UUID newAdminId) {
        var revokedRows = memberRepository.revokeAdmin(householdId);
        if (revokedRows == 0) {
            throw new HouseholdNotFoundException();
        }
        var grantedRows = memberRepository.grantAdmin(newAdminId);
        if (grantedRows == 0) {
            throw new MemberNotFoundException();
        }
    }

    private InviteResponse toInviteResponse(InviteEntity invite) {
        return new InviteResponse(invite.token(), invite.validUntil());
    }
}
