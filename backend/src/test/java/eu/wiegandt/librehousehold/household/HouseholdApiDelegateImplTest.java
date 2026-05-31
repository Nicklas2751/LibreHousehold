package eu.wiegandt.librehousehold.household;

import eu.wiegandt.librehousehold.model.HouseholdSetup;
import eu.wiegandt.librehousehold.model.HouseholdSetupResponse;
import org.instancio.Instancio;
import org.instancio.junit.InstancioExtension;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

@ExtendWith({MockitoExtension.class, InstancioExtension.class})
class HouseholdApiDelegateImplTest {

    @Mock
    private HouseholdSetupService householdSetupService;

    @Mock
    private HouseholdManagementService householdManagementService;

    @InjectMocks
    private HouseholdApiDelegateImpl householdApiDelegate;

    @Nested
    class setupHousehold {

        @Test
        void emptyBody_throwsHouseholdSetupIsRequiredException() {
            // when / then
            assertThatThrownBy(() -> householdApiDelegate.setupHousehold(Optional.empty()))
                    .isInstanceOf(HouseholdSetupIsRequiredException.class)
                    .hasMessageContaining("The body of setup must be set!");
        }

        @Test
        void validSetup_delegatesToServiceAndReturns200() {
            // given
            var setup = Instancio.create(HouseholdSetup.class);
            var response = Instancio.create(HouseholdSetupResponse.class);
            doReturn(response).when(householdSetupService).setupHousehold(setup);

            // when
            var result = householdApiDelegate.setupHousehold(Optional.of(setup));

            // then
            verify(householdSetupService).setupHousehold(setup);
            assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(result.getBody()).isEqualTo(response);
        }
    }
}
