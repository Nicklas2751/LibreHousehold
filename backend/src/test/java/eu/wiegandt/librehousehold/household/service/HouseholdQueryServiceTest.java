package eu.wiegandt.librehousehold.household.service;
import eu.wiegandt.librehousehold.household.exception.*;
import eu.wiegandt.librehousehold.household.mapper.*;
import eu.wiegandt.librehousehold.household.model.*;
import eu.wiegandt.librehousehold.household.repository.*;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

@ExtendWith(MockitoExtension.class)
class HouseholdQueryServiceTest {

    @Mock
    private HouseholdRepository householdRepository;

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
}
