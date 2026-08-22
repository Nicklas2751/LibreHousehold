package eu.wiegandt.librehousehold.household.service;

import eu.wiegandt.librehousehold.household.MemberDeletion;
import eu.wiegandt.librehousehold.household.MemberQuery;
import eu.wiegandt.librehousehold.household.MemberRemoved;
import eu.wiegandt.librehousehold.household.exception.InvalidInviteException;
import eu.wiegandt.librehousehold.household.exception.MemberAlreadyExistsException;
import eu.wiegandt.librehousehold.household.exception.MemberNotFoundException;
import eu.wiegandt.librehousehold.household.mapper.MemberMapper;
import eu.wiegandt.librehousehold.household.mapper.MemberRegistrationMapper;
import eu.wiegandt.librehousehold.household.model.MemberEntity;
import eu.wiegandt.librehousehold.household.model.MemberNameProjection;
import eu.wiegandt.librehousehold.household.repository.HouseholdRepository;
import eu.wiegandt.librehousehold.household.repository.InviteRepository;
import eu.wiegandt.librehousehold.household.repository.MemberRepository;
import eu.wiegandt.librehousehold.model.InviteInfo;
import eu.wiegandt.librehousehold.model.Member;
import eu.wiegandt.librehousehold.model.MemberRegistration;
import eu.wiegandt.librehousehold.model.MemberUpdate;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static java.util.stream.Collectors.toMap;

@Service
public class MemberManagementService implements MemberQuery, MemberDeletion {

    private static final boolean JOINED_MEMBER_IS_ADMIN = false;

    private final MemberRepository memberRepository;
    private final HouseholdRepository householdRepository;
    private final InviteRepository inviteRepository;
    private final MemberMapper memberMapper;
    private final MemberRegistrationMapper memberRegistrationMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final AccountService accountService;

    public MemberManagementService(MemberRepository memberRepository,
                                   HouseholdRepository householdRepository,
                                   InviteRepository inviteRepository,
                                   MemberMapper memberMapper,
                                   MemberRegistrationMapper memberRegistrationMapper,
                                   ApplicationEventPublisher eventPublisher,
                                   AccountService accountService) {
        this.memberRepository = memberRepository;
        this.householdRepository = householdRepository;
        this.inviteRepository = inviteRepository;
        this.memberMapper = memberMapper;
        this.memberRegistrationMapper = memberRegistrationMapper;
        this.eventPublisher = eventPublisher;
        this.accountService = accountService;
    }

    public List<Member> getMembers(UUID householdId) {
        return memberRepository.findByHouseholdId(householdId).stream()
                .map(memberMapper::toMember)
                .toList();
    }

    @Override
    public Member getMember(UUID memberId) {
        return memberRepository.findById(memberId)
                .map(memberMapper::toMember)
                .orElseThrow(MemberNotFoundException::new);
    }

    public InviteInfo resolveInvite(UUID token) {
        var invite = inviteRepository.findByToken(token)
                .filter(i -> !i.validUntil().isBefore(LocalDate.now()))
                .orElseThrow(InvalidInviteException::new);
        var householdName = householdRepository.findNameById(invite.householdId()).orElse("");
        return new InviteInfo(invite.householdId(), householdName, invite.validUntil());
    }

    @Transactional
    public Member joinHousehold(UUID token, MemberRegistration registration) {
        var invite = inviteRepository.findByToken(token)
                .filter(i -> !i.validUntil().isBefore(LocalDate.now()))
                .orElseThrow(InvalidInviteException::new);
        MemberEntity saved;
        try {
            saved = memberRepository.save(
                    memberRegistrationMapper.toMemberEntity(registration, invite.householdId(), JOINED_MEMBER_IS_ADMIN));
        } catch (DataIntegrityViolationException _) {
            throw new MemberAlreadyExistsException();
        }
        accountService.createAccount(saved.getId(), registration.getLocalRegistration().getPassword());
        return memberMapper.toMember(saved);
    }

    @Transactional
    public void updateMember(UUID memberId, MemberUpdate update) {
        try {
            var name = update.getName();
            if (name.isPresent()) {
                var rows = memberRepository.updateName(memberId, name.get());
                if (rows == 0) throw new MemberNotFoundException();
            }

            var email = update.getEmail();
            if (email.isPresent()) {
                var rows = memberRepository.updateEmail(memberId, email.get());
                if (rows == 0) throw new MemberNotFoundException();
            }
        } catch (DataIntegrityViolationException _) {
            throw new MemberAlreadyExistsException();
        }
    }

    @Override
    @Transactional
    public void removeMember(UUID memberId) {
        if (!memberRepository.existsById(memberId)) {
            throw new MemberNotFoundException();
        }
        memberRepository.deleteById(memberId);
        eventPublisher.publishEvent(new MemberRemoved(memberId));
    }

    @Override
    public Map<UUID, String> findMemberNamesByIds(Collection<UUID> memberIds) {
        if (memberIds.isEmpty()) {
            return Map.of();
        }
        return memberRepository.findNamesByIds(memberIds).stream()
                .collect(toMap(MemberNameProjection::id, MemberNameProjection::name));
    }

    @Override
    public List<UUID> findMemberIdsByHouseholdId(UUID householdId) {
        return memberRepository.findByHouseholdId(householdId).stream()
                .map(MemberEntity::getId)
                .toList();
    }

    @Override
    public boolean memberExistsById(UUID memberId) {
        return memberRepository.existsById(memberId);
    }

    @Override
    public boolean isAdmin(UUID memberId) {
        return memberRepository.findById(memberId)
                .map(MemberEntity::isAdmin)
                .orElse(false);
    }

    public boolean existsByEmail(String email) {
        return memberRepository.existsByEmail(email);
    }

    public boolean isEmailAvailable(String email) {
        return !existsByEmail(email);
    }

    public Optional<UUID> findMemberIdByEmail(String email) {
        return memberRepository.findByEmail(email).map(MemberEntity::getId);
    }
}
