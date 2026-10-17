package eu.wiegandt.librehousehold.household.service;

import eu.wiegandt.librehousehold.household.AccountRegistered;
import eu.wiegandt.librehousehold.household.ExternalIdentity;
import eu.wiegandt.librehousehold.household.LocalLoginProperties;
import eu.wiegandt.librehousehold.household.PendingSocialRegistrationStore;
import eu.wiegandt.librehousehold.household.exception.HouseholdAlreadyExistsException;
import eu.wiegandt.librehousehold.household.exception.LocalLoginDisabledException;
import eu.wiegandt.librehousehold.household.exception.MemberAlreadyExistsException;
import eu.wiegandt.librehousehold.household.exception.NoPendingSocialRegistrationException;
import eu.wiegandt.librehousehold.household.mapper.HouseholdSetupMapper;
import eu.wiegandt.librehousehold.household.mapper.MemberMapper;
import eu.wiegandt.librehousehold.household.model.HouseholdEntity;
import eu.wiegandt.librehousehold.household.model.InviteEntity;
import eu.wiegandt.librehousehold.household.model.MemberEntity;
import eu.wiegandt.librehousehold.household.repository.HouseholdRepository;
import eu.wiegandt.librehousehold.household.repository.InviteRepository;
import eu.wiegandt.librehousehold.household.repository.MemberRepository;
import eu.wiegandt.librehousehold.model.Household;
import eu.wiegandt.librehousehold.model.HouseholdSetup;
import eu.wiegandt.librehousehold.model.HouseholdSetupResponse;
import eu.wiegandt.librehousehold.model.LocalRegistration;
import eu.wiegandt.librehousehold.model.Member;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

@Service
@EnableConfigurationProperties(LocalLoginProperties.class)
public class HouseholdSetupService {

    private static final int INVITE_VALIDITY_DAYS = 7;
    private static final boolean SETUP_MEMBER_IS_ADMIN = true;

    private final HouseholdRepository householdRepository;
    private final MemberRepository memberRepository;
    private final InviteRepository inviteRepository;
    private final HouseholdSetupMapper householdSetupMapper;
    private final MemberMapper memberMapper;
    private final AccountService accountService;
    private final AccountSessionAuthenticator accountSessionAuthenticator;
    private final ApplicationEventPublisher eventPublisher;
    private final PendingSocialRegistrationStore pendingSocialRegistrationStore;
    private final LocalLoginProperties localLoginProperties;

    public HouseholdSetupService(HouseholdRepository householdRepository, MemberRepository memberRepository,
                          InviteRepository inviteRepository, HouseholdSetupMapper householdSetupMapper,
                          MemberMapper memberMapper, AccountService accountService,
                          AccountSessionAuthenticator accountSessionAuthenticator,
                          ApplicationEventPublisher eventPublisher,
                          PendingSocialRegistrationStore pendingSocialRegistrationStore,
                          LocalLoginProperties localLoginProperties) {
        this.householdRepository = householdRepository;
        this.memberRepository = memberRepository;
        this.inviteRepository = inviteRepository;
        this.householdSetupMapper = householdSetupMapper;
        this.memberMapper = memberMapper;
        this.accountService = accountService;
        this.accountSessionAuthenticator = accountSessionAuthenticator;
        this.eventPublisher = eventPublisher;
        this.pendingSocialRegistrationStore = pendingSocialRegistrationStore;
        this.localLoginProperties = localLoginProperties;
    }

    /**
     * Sets up the household with its admin. With {@code localRegistration}, the admin gets a
     * password account, unless this instance turned local login off. Without it, the admin is registered via the social login that left a
     * pending registration in this session: the e-mail address then comes from the external
     * identity, never from the request.
     */
    @Transactional
    public HouseholdSetupResponse setupHousehold(HouseholdSetup setup) {
        if (setup.getLocalRegistration().isPresent()) {
            assertLocalLoginEnabled();
            var savedHousehold = saveHousehold(setup.getHousehold());
            registerLocalMember(setup.getMember(), savedHousehold.id(), setup.getLocalRegistration().get());
            return createInvite(savedHousehold);
        }
        var identity = pendingSocialRegistrationStore.find().orElseThrow(NoPendingSocialRegistrationException::new);
        var savedHousehold = saveHousehold(setup.getHousehold());
        registerFederatedMember(setup.getMember(), savedHousehold.id(), identity);
        return createInvite(savedHousehold);
    }

    private void assertLocalLoginEnabled() {
        if (!localLoginProperties.enabled()) {
            throw new LocalLoginDisabledException();
        }
    }

    private HouseholdEntity saveHousehold(Household household) {
        try {
            return householdRepository.save(householdSetupMapper.toHouseholdEntity(household));
        } catch (DataIntegrityViolationException _) {
            throw new HouseholdAlreadyExistsException();
        }
    }

    private void registerLocalMember(Member member, UUID householdId, LocalRegistration localRegistration) {
        var savedMember = saveMember(memberMapper.toMemberEntity(member, householdId, SETUP_MEMBER_IS_ADMIN));
        accountService.createAccount(savedMember.getId(), localRegistration.getPassword());
        eventPublisher.publishEvent(new AccountRegistered(savedMember.getId(), member.getEmail()));
        accountSessionAuthenticator.authenticateAndPersistSession(member.getEmail(), localRegistration.getPassword());
    }

    // No AccountRegistered event: it triggers the verification e-mail, but the provider already
    // verified the address.
    private void registerFederatedMember(Member member, UUID householdId, ExternalIdentity identity) {
        var savedMember = saveMember(
                memberMapper.toMemberEntityWithEmail(member, householdId, SETUP_MEMBER_IS_ADMIN, identity.email()));
        accountService.createFederatedAccount(savedMember.getId());
        try {
            accountService.linkExternalIdentity(savedMember.getId(), identity.registrationId(), identity.subject());
        } catch (DataIntegrityViolationException _) {
            throw new MemberAlreadyExistsException();
        }
        pendingSocialRegistrationStore.consume();
        accountSessionAuthenticator.authenticateFederatedAndPersistSession(identity.email());
    }

    private MemberEntity saveMember(MemberEntity member) {
        try {
            return memberRepository.save(member);
        } catch (DataIntegrityViolationException _) {
            throw new MemberAlreadyExistsException();
        }
    }

    private HouseholdSetupResponse createInvite(HouseholdEntity savedHousehold) {
        var invite = inviteRepository.save(new InviteEntity(
                null,
                savedHousehold.id(),
                UUID.randomUUID(),
                LocalDate.now().plusDays(INVITE_VALIDITY_DAYS)
        ));
        return new HouseholdSetupResponse(
                householdSetupMapper.toApiModel(savedHousehold),
                invite.token(),
                invite.validUntil()
        );
    }
}
