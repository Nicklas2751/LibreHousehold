package eu.wiegandt.librehousehold.household.service;

import eu.wiegandt.librehousehold.household.AccountRegistered;
import eu.wiegandt.librehousehold.household.ExternalIdentity;
import eu.wiegandt.librehousehold.household.LocalLoginProperties;
import eu.wiegandt.librehousehold.household.MemberQuery;
import eu.wiegandt.librehousehold.household.MemberRemoved;
import eu.wiegandt.librehousehold.household.PasswordResetRequested;
import eu.wiegandt.librehousehold.household.PendingSocialRegistrationStore;
import eu.wiegandt.librehousehold.household.VerificationEmailRequested;
import eu.wiegandt.librehousehold.household.exception.EmailNotVerifiedException;
import eu.wiegandt.librehousehold.household.exception.HouseholdAdminCannotBeRemovedException;
import eu.wiegandt.librehousehold.household.exception.InvalidInviteException;
import eu.wiegandt.librehousehold.household.exception.LocalLoginDisabledException;
import eu.wiegandt.librehousehold.household.exception.MemberAlreadyExistsException;
import eu.wiegandt.librehousehold.household.exception.MemberNotFoundException;
import eu.wiegandt.librehousehold.household.exception.NoPendingSocialRegistrationException;
import eu.wiegandt.librehousehold.household.mapper.MemberMapper;
import eu.wiegandt.librehousehold.household.mapper.MemberRegistrationMapper;
import eu.wiegandt.librehousehold.household.model.MemberEntity;
import eu.wiegandt.librehousehold.household.model.MemberNameProjection;
import eu.wiegandt.librehousehold.household.repository.HouseholdRepository;
import eu.wiegandt.librehousehold.household.repository.InviteRepository;
import eu.wiegandt.librehousehold.household.repository.MemberRepository;
import eu.wiegandt.librehousehold.model.InviteInfo;
import eu.wiegandt.librehousehold.model.LocalRegistration;
import eu.wiegandt.librehousehold.model.Member;
import eu.wiegandt.librehousehold.model.MemberRegistration;
import eu.wiegandt.librehousehold.model.MemberUpdate;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;

import static java.util.stream.Collectors.toMap;

@Service
@EnableConfigurationProperties(LocalLoginProperties.class)
public class MemberManagementService implements MemberQuery {

    private static final boolean JOINED_MEMBER_IS_ADMIN = false;

    private final MemberRepository memberRepository;
    private final HouseholdRepository householdRepository;
    private final InviteRepository inviteRepository;
    private final MemberMapper memberMapper;
    private final MemberRegistrationMapper memberRegistrationMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final AccountService accountService;
    private final AccountSessionAuthenticator accountSessionAuthenticator;
    private final PendingSocialRegistrationStore pendingSocialRegistrationStore;
    private final LocalLoginProperties localLoginProperties;

    public MemberManagementService(MemberRepository memberRepository,
                                   HouseholdRepository householdRepository,
                                   InviteRepository inviteRepository,
                                   MemberMapper memberMapper,
                                   MemberRegistrationMapper memberRegistrationMapper,
                                   ApplicationEventPublisher eventPublisher,
                                   AccountService accountService,
                                   AccountSessionAuthenticator accountSessionAuthenticator,
                                   PendingSocialRegistrationStore pendingSocialRegistrationStore,
                                   LocalLoginProperties localLoginProperties) {
        this.memberRepository = memberRepository;
        this.householdRepository = householdRepository;
        this.inviteRepository = inviteRepository;
        this.memberMapper = memberMapper;
        this.memberRegistrationMapper = memberRegistrationMapper;
        this.eventPublisher = eventPublisher;
        this.accountService = accountService;
        this.accountSessionAuthenticator = accountSessionAuthenticator;
        this.pendingSocialRegistrationStore = pendingSocialRegistrationStore;
        this.localLoginProperties = localLoginProperties;
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

    public Member getMember(UUID householdId, UUID memberId) {
        return memberRepository.findByIdAndHouseholdId(memberId, householdId)
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

    /**
     * Adds a member to the invite's household. With {@code localRegistration}, the member gets a
     * password account, unless this instance turned local login off. Without it, the member is registered via the social login that left a
     * pending registration in this session: the e-mail address then comes from the external
     * identity, never from the request.
     */
    @Transactional
    public Member joinHousehold(UUID token, MemberRegistration registration) {
        var invite = inviteRepository.findByToken(token)
                .filter(i -> !i.validUntil().isBefore(LocalDate.now()))
                .orElseThrow(InvalidInviteException::new);
        if (registration.getLocalRegistration().isPresent()) {
            assertLocalLoginEnabled();
            return registerLocalMember(registration, invite.householdId(), registration.getLocalRegistration().get());
        }
        var identity = pendingSocialRegistrationStore.find().orElseThrow(NoPendingSocialRegistrationException::new);
        return registerFederatedMember(registration, invite.householdId(), identity);
    }

    private void assertLocalLoginEnabled() {
        if (!localLoginProperties.enabled()) {
            throw new LocalLoginDisabledException();
        }
    }

    private Member registerLocalMember(MemberRegistration registration, UUID householdId,
                                       LocalRegistration localRegistration) {
        var saved = saveMember(memberRegistrationMapper.toMemberEntity(registration, householdId, JOINED_MEMBER_IS_ADMIN));
        accountService.createAccount(saved.getId(), localRegistration.getPassword());
        eventPublisher.publishEvent(new AccountRegistered(saved.getId(), registration.getEmail()));
        accountSessionAuthenticator.authenticateAndPersistSession(registration.getEmail(), localRegistration.getPassword());
        return memberMapper.toMember(saved);
    }

    // No AccountRegistered event: it triggers the verification e-mail, but the provider already
    // verified the address.
    private Member registerFederatedMember(MemberRegistration registration, UUID householdId, ExternalIdentity identity) {
        var saved = saveMember(memberRegistrationMapper.toMemberEntityWithEmail(
                registration, householdId, JOINED_MEMBER_IS_ADMIN, identity.email()));
        accountService.createFederatedAccount(saved.getId());
        try {
            accountService.linkExternalIdentity(saved.getId(), identity.registrationId(), identity.subject());
        } catch (DataIntegrityViolationException _) {
            throw new MemberAlreadyExistsException();
        }
        pendingSocialRegistrationStore.consume();
        accountSessionAuthenticator.authenticateFederatedAndPersistSession(identity.email());
        return memberMapper.toMember(saved);
    }

    private MemberEntity saveMember(MemberEntity member) {
        try {
            return memberRepository.save(member);
        } catch (DataIntegrityViolationException _) {
            throw new MemberAlreadyExistsException();
        }
    }

    @Transactional
    public void updateMember(UUID householdId, UUID memberId, MemberUpdate update) {
        if (!memberRepository.existsByIdAndHouseholdId(memberId, householdId)) {
            throw new MemberNotFoundException();
        }
        if (update.getEmail().isPresent() && !accountService.isEmailVerified(memberId)) {
            throw new EmailNotVerifiedException();
        }
        try {
            update.getName().ifPresent(name -> memberRepository.updateName(memberId, name));

            update.getEmail().ifPresent(email -> memberRepository.updateEmail(memberId, email));
        } catch (DataIntegrityViolationException _) {
            throw new MemberAlreadyExistsException();
        }
    }

    @Transactional
    public void leaveHousehold(UUID memberId) {
        var member = memberRepository.findById(memberId).orElseThrow(MemberNotFoundException::new);
        assertMemberIsNotHouseholdAdmin(member);
        deleteMemberAndPublishEvent(member);
    }

    @Transactional
    public void removeMember(UUID householdId, UUID memberId) {
        var member = memberRepository.findByIdAndHouseholdId(memberId, householdId)
                .orElseThrow(MemberNotFoundException::new);
        assertMemberIsNotHouseholdAdmin(member);
        deleteMemberAndPublishEvent(member);
    }

    private void assertMemberIsNotHouseholdAdmin(MemberEntity member) {
        if (member.isAdmin()) {
            throw new HouseholdAdminCannotBeRemovedException();
        }
    }

    private void deleteMemberAndPublishEvent(MemberEntity member) {
        memberRepository.deleteById(member.id());
        var householdName = householdRepository.findNameById(member.householdId()).orElse("");
        eventPublisher.publishEvent(new MemberRemoved(member.id(), member.name(), member.email(), householdName));
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

    @Override
    public Optional<String> findEmailByExternalIdentity(String providerId, String externalSubject) {
        return accountService.findMemberIdByExternalIdentity(providerId, externalSubject)
                .flatMap(memberRepository::findById)
                .map(MemberEntity::email);
    }

    @Override
    public boolean isEmailVerified(UUID memberId) {
        return accountService.isEmailVerified(memberId);
    }

    @Override
    public boolean isSocialLoginOnly(UUID memberId) {
        return !accountService.hasPassword(memberId);
    }

    // @Transactional is required: AccountRegistrationListener handles VerificationEmailRequested as an
    // @ApplicationModuleListener (AFTER_COMMIT), which Spring silently skips when the event is
    // published outside a transaction, so the verification e-mail would never be sent.
    @Transactional
    public void resendVerificationEmail(UUID memberId) {
        var member = memberRepository.findById(memberId).orElseThrow(MemberNotFoundException::new);
        eventPublisher.publishEvent(new VerificationEmailRequested(member.getId(), member.email()));
    }

    // @Transactional is required, not optional: PasswordResetRequestedListener is an
    // @ApplicationModuleListener (@TransactionalEventListener(phase = AFTER_COMMIT) under the hood),
    // which Spring silently never invokes if no transaction is active at publish time — confirmed via
    // a live event_publication row stuck with a null completion_date during manual E2E verification.
    @Transactional
    public void requestPasswordReset(String email) {
        // Accounts without a local password (social login) get no reset link, but the caller sees no
        // difference, so the endpoint still does not reveal which kind of account an e-mail belongs to.
        memberRepository.findByEmail(email)
                .filter(member -> accountService.hasPassword(member.getId()))
                .ifPresent(member -> eventPublisher.publishEvent(new PasswordResetRequested(member.getId(), email)));
    }
}
