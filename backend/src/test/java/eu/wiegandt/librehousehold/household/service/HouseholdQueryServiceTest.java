package eu.wiegandt.librehousehold.household.service;
import eu.wiegandt.librehousehold.household.exception.*;
import eu.wiegandt.librehousehold.household.mapper.*;
import eu.wiegandt.librehousehold.household.model.*;
import eu.wiegandt.librehousehold.household.repository.*;
import eu.wiegandt.librehousehold.model.Household;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;

@ExtendWith(MockitoExtension.class)
class HouseholdQueryServiceTest {

    @Mock
    private HouseholdRepository householdRepository;

    @Spy
    private HouseholdSetupMapper householdMapper = Mappers.getMapper(HouseholdSetupMapper.class);

    @InjectMocks
    private HouseholdQueryService householdQueryService;

    @Nested
    class householdExists {

        @Test
        void existingId_returnsTrue() {
            // given
            var id = UUID.randomUUID();
            doReturn(true).when(householdRepository).existsById(id);

            // when
            var result = householdQueryService.householdExists(id);

            // then
            assertThat(result).isTrue();
        }

        @Test
        void unknownId_returnsFalse() {
            // given
            var id = UUID.randomUUID();
            doReturn(false).when(householdRepository).existsById(id);

            // when
            var result = householdQueryService.householdExists(id);

            // then
            assertThat(result).isFalse();
        }
    }

    @Nested
    class getHousehold {

        @Test
        void existingId_returnsMappedHousehold() {
            // given
            var id = UUID.randomUUID();
            var entity = new HouseholdEntity(id, "Doe Family", "image-bytes");
            doReturn(Optional.of(entity)).when(householdRepository).findById(id);
            var expected = new Household(id, "Doe Family").image("image-bytes");

            // when
            var result = householdQueryService.getHousehold(id);

            // then
            assertThat(result).usingRecursiveComparison().isEqualTo(expected);
        }

        @Test
        void unknownId_throwsHouseholdNotFoundException() {
            // given
            var id = UUID.randomUUID();
            doReturn(Optional.empty()).when(householdRepository).findById(id);

            // when / then
            assertThatThrownBy(() -> householdQueryService.getHousehold(id))
                    .isInstanceOf(HouseholdNotFoundException.class);
        }
    }
}
