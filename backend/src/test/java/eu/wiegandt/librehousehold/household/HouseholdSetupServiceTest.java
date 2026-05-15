package eu.wiegandt.librehousehold.household;

import eu.wiegandt.librehousehold.model.Household;
import eu.wiegandt.librehousehold.model.HouseholdSetup;
import eu.wiegandt.librehousehold.model.Member;
import org.instancio.Instancio;
import org.instancio.junit.InstancioExtension;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.instancio.Select.field;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith({MockitoExtension.class, InstancioExtension.class})
class HouseholdSetupServiceTest {

    @Mock
    private HouseholdRepository householdRepository;

    @Mock
    private MemberRepository memberRepository;

    @Spy
    private HouseholdSetupMapper householdSetupMapper = Mappers.getMapper(HouseholdSetupMapper.class);

    @Spy
    private MemberMapper memberMapper = Mappers.getMapper(MemberMapper.class);

    @InjectMocks
    private HouseholdSetupService service;

    @Nested
    class setupHousehold {

        @Test
        void dataIntegrityViolationOnSave_throwsHouseholdAlreadyExistsException() {
            // given
            doThrow(DataIntegrityViolationException.class).when(memberRepository).save(any(MemberEntity.class));

            // when / then
            assertThatThrownBy(() -> service.setupHousehold(buildSetup()))
                    .isInstanceOf(HouseholdAlreadyExistsException.class);
        }

        @Test
        void validSetup_savesMemberEntityDerivedFromSetup() {
            // given
            var member = Instancio.create(Member.class);
            var household = Instancio.of(Household.class)
                    .set(field(Household::getAdmin), member.getId())
                    .create();
            var expectedMemberEntity = memberMapper.toMemberEntity(member);
            doReturn(Instancio.create(HouseholdEntity.class)).when(householdRepository).save(any(HouseholdEntity.class));

            // when
            service.setupHousehold(new HouseholdSetup(household, member));

            // then
            verify(memberRepository).save(expectedMemberEntity);
        }

        @Test
        void validSetup_savesMemberBeforeSavingHousehold() {
            // given
            doReturn(Instancio.create(HouseholdEntity.class)).when(householdRepository).save(any(HouseholdEntity.class));

            // when
            service.setupHousehold(buildSetup());

            // then
            var order = inOrder(memberRepository, householdRepository);
            order.verify(memberRepository).save(any(MemberEntity.class));
            order.verify(householdRepository).save(any(HouseholdEntity.class));
        }

        @Test
        void validSetup_returnsHouseholdWithFieldsFromSetup() {
            // given
            var member = Instancio.create(Member.class);
            var household = Instancio.of(Household.class)
                    .set(field(Household::getAdmin), member.getId())
                    .create();
            var savedHouseholdEntity = householdSetupMapper.toHouseholdEntity(household);
            var expectedResult = householdSetupMapper.toApiModel(savedHouseholdEntity);
            doReturn(savedHouseholdEntity).when(householdRepository).save(any(HouseholdEntity.class));

            // when
            var result = service.setupHousehold(new HouseholdSetup(household, member));

            // then
            assertThat(result).isEqualTo(expectedResult);
        }
    }

    private HouseholdSetup buildSetup() {
        var member = Instancio.create(Member.class);
        var household = Instancio.of(Household.class)
                .set(field(Household::getAdmin), member.getId())
                .create();
        return new HouseholdSetup(household, member);
    }
}
