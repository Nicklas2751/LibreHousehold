package eu.wiegandt.librehousehold.household;

import eu.wiegandt.librehousehold.model.Household;
import eu.wiegandt.librehousehold.model.HouseholdSetup;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class HouseholdSetupService {

    private final HouseholdRepository householdRepository;
    private final MemberRepository memberRepository;
    private final HouseholdSetupMapper householdSetupMapper;
    private final MemberMapper memberMapper;

    HouseholdSetupService(HouseholdRepository householdRepository, MemberRepository memberRepository,
                          HouseholdSetupMapper householdSetupMapper, MemberMapper memberMapper) {
        this.householdRepository = householdRepository;
        this.memberRepository = memberRepository;
        this.householdSetupMapper = householdSetupMapper;
        this.memberMapper = memberMapper;
    }

    @Transactional
    Household setupHousehold(HouseholdSetup setup) {
        try {
            memberRepository.save(memberMapper.toMemberEntity(setup.getMember()));
            var savedHousehold = householdRepository.save(householdSetupMapper.toHouseholdEntity(setup.getHousehold()));
            return householdSetupMapper.toApiModel(savedHousehold);
        } catch (DataIntegrityViolationException e) {
            throw new HouseholdAlreadyExistsException();
        }
    }
}
