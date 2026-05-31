package eu.wiegandt.librehousehold.household;

import eu.wiegandt.librehousehold.model.InviteInfo;
import eu.wiegandt.librehousehold.model.Member;
import eu.wiegandt.librehousehold.model.MemberRegistration;
import eu.wiegandt.librehousehold.model.MemberUpdate;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
class MemberManagementService {

    private final MemberRepository memberRepository;
    private final HouseholdRepository householdRepository;
    private final InviteRepository inviteRepository;
    private final MemberMapper memberMapper;
    private final ApplicationEventPublisher eventPublisher;

    MemberManagementService(MemberRepository memberRepository,
                            HouseholdRepository householdRepository,
                            InviteRepository inviteRepository,
                            MemberMapper memberMapper,
                            ApplicationEventPublisher eventPublisher) {
        this.memberRepository = memberRepository;
        this.householdRepository = householdRepository;
        this.inviteRepository = inviteRepository;
        this.memberMapper = memberMapper;
        this.eventPublisher = eventPublisher;
    }

    List<Member> getMembers(UUID householdId) {
        return memberRepository.findByHouseholdId(householdId).stream()
                .map(memberMapper::toMember)
                .toList();
    }

    Member getMember(UUID memberId) {
        return memberRepository.findById(memberId)
                .map(memberMapper::toMember)
                .orElseThrow(MemberNotFoundException::new);
    }

    InviteInfo resolveInvite(UUID token) {
        var invite = inviteRepository.findByToken(token)
                .filter(i -> !i.validUntil().isBefore(LocalDate.now()))
                .orElseThrow(InvalidInviteException::new);
        var householdName = householdRepository.findNameById(invite.householdId()).orElse("");
        return new InviteInfo(invite.householdId(), householdName, invite.validUntil());
    }

    @Transactional
    Member joinHousehold(UUID token, MemberRegistration registration) {
        var invite = inviteRepository.findByToken(token)
                .filter(i -> !i.validUntil().isBefore(LocalDate.now()))
                .orElseThrow(InvalidInviteException::new);
        try {
            var saved = memberRepository.save(new MemberEntity(
                    registration.getId(),
                    registration.getName(),
                    registration.getEmail(),
                    registration.getAvatar().orElse(null),
                    invite.householdId(),
                    false
            ));
            return memberMapper.toMember(saved);
        } catch (DataIntegrityViolationException e) {
            throw new MemberAlreadyExistsException();
        }
    }

    @Transactional
    void updateMember(UUID memberId, MemberUpdate update) {
        try {
            if (update.getName().isPresent()) {
                var rows = memberRepository.updateName(memberId, update.getName().get());
                if (rows == 0) throw new MemberNotFoundException();
            }
            if (update.getEmail().isPresent()) {
                var rows = memberRepository.updateEmail(memberId, update.getEmail().get());
                if (rows == 0) throw new MemberNotFoundException();
            }
        } catch (DataIntegrityViolationException e) {
            throw new MemberAlreadyExistsException();
        }
    }

    @Transactional
    void removeMember(UUID memberId) {
        if (!memberRepository.existsById(memberId)) {
            throw new MemberNotFoundException();
        }
        memberRepository.deleteById(memberId);
        eventPublisher.publishEvent(new MemberRemoved(memberId));
    }
}
