package eu.wiegandt.librehousehold.household.controller;

import eu.wiegandt.librehousehold.household.exception.HouseholdAlreadyExistsException;
import eu.wiegandt.librehousehold.household.exception.HouseholdSetupIsRequiredException;
import eu.wiegandt.librehousehold.household.service.AccountSessionSignIn;
import eu.wiegandt.librehousehold.household.service.HouseholdManagementService;
import eu.wiegandt.librehousehold.household.service.HouseholdSetupService;
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
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith({MockitoExtension.class, InstancioExtension.class})
class HouseholdApiDelegateImplTest {

    @Mock
    private HouseholdSetupService householdSetupService;

    @Mock
    private HouseholdManagementService householdManagementService;

    @Mock
    private AccountSessionSignIn accountSessionSignIn;

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

        @Test
        void anySetup_signsInSetupMemberId() {
            // given
            var setup = Instancio.create(HouseholdSetup.class);

            // when
            householdApiDelegate.setupHousehold(Optional.of(setup));

            // then
            verify(accountSessionSignIn).signIn(setup.getMember().getId());
        }

        @Test
        void serviceThrows_doesNotSignIn() {
            // given
            var setup = Instancio.create(HouseholdSetup.class);
            doThrow(HouseholdAlreadyExistsException.class).when(householdSetupService).setupHousehold(setup);

            // when
            catchThrowable(() -> householdApiDelegate.setupHousehold(Optional.of(setup)));

            // then
            verifyNoInteractions(accountSessionSignIn);
        }
    }
}
