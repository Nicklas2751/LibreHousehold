package eu.wiegandt.librehousehold.household.service;

import eu.wiegandt.librehousehold.core.AccountRegistration;
import eu.wiegandt.librehousehold.household.MemberDeletion;
import eu.wiegandt.librehousehold.household.MemberEmailChanged;
import eu.wiegandt.librehousehold.household.MemberQuery;
import eu.wiegandt.librehousehold.household.MemberRemoved;
import eu.wiegandt.librehousehold.household.exception.InvalidInviteException;
import eu.wiegandt.librehousehold.household.exception.MemberAlreadyExistsException;
import eu.wiegandt.librehousehold.household.exception.MemberNotFoundException;
import eu.wiegandt.librehousehold.household.mapper.MemberMapper;
import eu.wiegandt.librehousehold.household.model.MemberEntity;
import eu.wiegandt.librehousehold.household.model.MemberNameProjection;
import eu.wiegandt.librehousehold.household.repository.HouseholdRepository;
import eu.wiegandt.librehousehold.household.repository.InviteRepository;
import eu.wiegandt.librehousehold.household.repository.MemberRepository;
import eu.wiegandt.librehousehold.model.InviteInfo;
import eu.wiegandt.librehousehold.model.LocalMemberRegistration;
import eu.wiegandt.librehousehold.model.Member;
import eu.wiegandt.librehousehold.model.MemberUpdate;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;

import static java.util.stream.Collectors.toMap;

@Service
public class MemberManagementService implements MemberQuery, MemberDeletion {

    private final MemberRepository memberRepository;
    private final HouseholdRepository householdRepository;
    private final InviteRepository inviteRepository;
    private final MemberMapper memberMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final AccountRegistration accountRegistration;
    private final PasswordEncoder passwordEncoder;

    public MemberManagementService(MemberRepository memberRepository,
                                   HouseholdRepository householdRepository,
                                   InviteRepository inviteRepository,
                                   MemberMapper memberMapper,
                                   ApplicationEventPublisher eventPublisher,
                                   AccountRegistration accountRegistration,
                                   PasswordEncoder passwordEncoder) {
        this.memberRepository = memberRepository;
        this.householdRepository = householdRepository;
        this.inviteRepository = inviteRepository;
        this.memberMapper = memberMapper;
        this.eventPublisher = eventPublisher;
        this.accountRegistration = accountRegistration;
        this.passwordEncoder = passwordEncoder;
    }

    public List<Member> getMembers(UUID householdId) {
        return memberRepository.findByHouseholdId(householdId).stream()
                .map(memberMapper::toMember)
                .toList();
    }

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
    public Member joinHouseholdLocal(UUID token, LocalMemberRegistration registration) {
        var invite = inviteRepository.findByToken(token)
                .filter(i -> !i.validUntil().isBefore(LocalDate.now()))
                .orElseThrow(InvalidInviteException::new);
        var accountId = UUID.randomUUID();
        var encodedPassword = passwordEncoder.encode(registration.getPassword());
        accountRegistration.registerLocalAccount(accountId, registration.getEmail(), encodedPassword);
        var saved = memberRepository.save(new MemberEntity(
                accountId,
                registration.getName(),
                registration.getAvatar().orElse(null),
                invite.householdId(),
                false
        ));
        return memberMapper.toMember(saved);
    }

    @Transactional
    public Member joinHouseholdAuthenticated(UUID accountId, UUID token, String memberName, String memberAvatar) {
        if (memberExistsById(accountId)) {
            throw new MemberAlreadyExistsException();
        }
        var invite = inviteRepository.findByToken(token)
                .filter(i -> !i.validUntil().isBefore(LocalDate.now()))
                .orElseThrow(InvalidInviteException::new);
        var saved = memberRepository.save(new MemberEntity(accountId, memberName, memberAvatar, invite.householdId(), false));
        return memberMapper.toMember(saved);
    }

    @Transactional
    public void updateMember(UUID memberId, MemberUpdate update) {
        if (update.getName().isPresent()) {
            var rows = memberRepository.updateName(memberId, update.getName().get());
            if (rows == 0) throw new MemberNotFoundException();
        }
        if (update.getEmail().isPresent()) {
            eventPublisher.publishEvent(new MemberEmailChanged(memberId, update.getEmail().get()));
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

    @Override
    public Optional<UUID> findHouseholdIdByMemberId(UUID memberId) {
        return memberRepository.findById(memberId)
                .map(MemberEntity::householdId);
    }
}
