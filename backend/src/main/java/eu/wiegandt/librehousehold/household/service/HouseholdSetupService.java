package eu.wiegandt.librehousehold.household.service;

import eu.wiegandt.librehousehold.household.exception.HouseholdAlreadyExistsException;
import eu.wiegandt.librehousehold.household.exception.MemberAlreadyExistsException;
import eu.wiegandt.librehousehold.household.mapper.HouseholdSetupMapper;
import eu.wiegandt.librehousehold.household.mapper.MemberMapper;
import eu.wiegandt.librehousehold.household.model.HouseholdEntity;
import eu.wiegandt.librehousehold.household.model.InviteEntity;
import eu.wiegandt.librehousehold.household.model.MemberEntity;
import eu.wiegandt.librehousehold.household.repository.HouseholdRepository;
import eu.wiegandt.librehousehold.household.repository.InviteRepository;
import eu.wiegandt.librehousehold.household.repository.MemberRepository;
import eu.wiegandt.librehousehold.model.HouseholdSetup;
import eu.wiegandt.librehousehold.model.HouseholdSetupResponse;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

@Service
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

    public HouseholdSetupService(HouseholdRepository householdRepository, MemberRepository memberRepository,
                          InviteRepository inviteRepository, HouseholdSetupMapper householdSetupMapper,
                          MemberMapper memberMapper, AccountService accountService,
                          AccountSessionAuthenticator accountSessionAuthenticator) {
        this.householdRepository = householdRepository;
        this.memberRepository = memberRepository;
        this.inviteRepository = inviteRepository;
        this.householdSetupMapper = householdSetupMapper;
        this.memberMapper = memberMapper;
        this.accountService = accountService;
        this.accountSessionAuthenticator = accountSessionAuthenticator;
    }

    @Transactional
    public HouseholdSetupResponse setupHousehold(HouseholdSetup setup) {
        HouseholdEntity savedHousehold;
        try {
            savedHousehold = householdRepository.save(householdSetupMapper.toHouseholdEntity(setup.getHousehold()));
        } catch (DataIntegrityViolationException e) {
            throw new HouseholdAlreadyExistsException();
        }
        MemberEntity savedMember;
        try {
            savedMember = memberRepository.save(
                    memberMapper.toMemberEntity(setup.getMember(), savedHousehold.id(), SETUP_MEMBER_IS_ADMIN));
        } catch (DataIntegrityViolationException e) {
            throw new MemberAlreadyExistsException();
        }
        accountService.createAccount(savedMember.getId(), setup.getLocalRegistration().getPassword());
        accountSessionAuthenticator.authenticateAndPersistSession(
                setup.getMember().getEmail(), setup.getLocalRegistration().getPassword());
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
