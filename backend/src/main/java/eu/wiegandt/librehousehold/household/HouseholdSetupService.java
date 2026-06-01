package eu.wiegandt.librehousehold.household;

import eu.wiegandt.librehousehold.model.HouseholdSetup;
import eu.wiegandt.librehousehold.model.HouseholdSetupResponse;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

@Service
class HouseholdSetupService {

    private static final int INVITE_VALIDITY_DAYS = 7;

    private final HouseholdRepository householdRepository;
    private final MemberRepository memberRepository;
    private final InviteRepository inviteRepository;
    private final HouseholdSetupMapper householdSetupMapper;

    HouseholdSetupService(HouseholdRepository householdRepository, MemberRepository memberRepository,
                          InviteRepository inviteRepository, HouseholdSetupMapper householdSetupMapper) {
        this.householdRepository = householdRepository;
        this.memberRepository = memberRepository;
        this.inviteRepository = inviteRepository;
        this.householdSetupMapper = householdSetupMapper;
    }

    @Transactional
    HouseholdSetupResponse setupHousehold(HouseholdSetup setup) {
        HouseholdEntity savedHousehold;
        try {
            savedHousehold = householdRepository.save(householdSetupMapper.toHouseholdEntity(setup.getHousehold()));
        } catch (DataIntegrityViolationException e) {
            throw new HouseholdAlreadyExistsException();
        }
        try {
            memberRepository.save(new MemberEntity(
                    setup.getMember().getId(),
                    setup.getMember().getName(),
                    setup.getMember().getEmail(),
                    setup.getMember().getAvatar().orElse(null),
                    savedHousehold.id(),
                    true
            ));
        } catch (DataIntegrityViolationException e) {
            throw new MemberAlreadyExistsException();
        }
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
