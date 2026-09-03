package eu.wiegandt.librehousehold.household.service;

import eu.wiegandt.librehousehold.TestcontainersConfiguration;
import eu.wiegandt.librehousehold.household.exception.HouseholdNotFoundException;
import eu.wiegandt.librehousehold.household.mapper.HouseholdSetupMapper;
import eu.wiegandt.librehousehold.household.model.HouseholdEntity;
import eu.wiegandt.librehousehold.household.repository.HouseholdRepository;
import org.instancio.Instancio;
import org.instancio.junit.InstancioExtension;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {"librehousehold.security.oauth2-client.client-secret=test-client-secret"})
@Import(TestcontainersConfiguration.class)
@ExtendWith(InstancioExtension.class)
class HouseholdQueryServiceIT {

    @Autowired
    private HouseholdQueryService householdQueryService;

    @Autowired
    private HouseholdRepository householdRepository;

    @Autowired
    private HouseholdSetupMapper householdMapper;

    @Nested
    class getHousehold {

        @Test
        void householdNotFound_throwsHouseholdNotFoundException() {
            // given
            var unknownId = Instancio.create(UUID.class);

            // when / then
            assertThatThrownBy(() -> householdQueryService.getHousehold(unknownId))
                    .isInstanceOf(HouseholdNotFoundException.class);
        }

        @Test
        void householdFound_returnsHouseholdFromDatabase() {
            // given
            var savedHousehold = householdRepository.save(Instancio.create(HouseholdEntity.class));
            var expected = householdMapper.toApiModel(savedHousehold);

            // when
            var result = householdQueryService.getHousehold(savedHousehold.id());

            // then
            assertThat(result).usingRecursiveComparison().isEqualTo(expected);

            householdRepository.deleteById(savedHousehold.id());
        }
    }
}
