package eu.wiegandt.librehousehold.usersettings.controller;

import eu.wiegandt.librehousehold.model.UserPreferences;
import eu.wiegandt.librehousehold.usersettings.exception.MemberNotFoundException;
import eu.wiegandt.librehousehold.usersettings.service.UsersettingsService;
import org.instancio.Instancio;
import org.instancio.junit.InstancioExtension;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith({MockitoExtension.class, InstancioExtension.class})
class UsersettingsApiDelegateImplTest {

    @Mock
    private UsersettingsService service;

    @InjectMocks
    private UsersettingsApiDelegateImpl delegate;

    @Nested
    class updatePreferences {

        @Test
        void validInput_delegatesToServiceAndReturns200() {
            // given
            var householdId = UUID.randomUUID();
            var memberId = UUID.randomUUID();
            var request = Instancio.create(UserPreferences.class);
            var saved = Instancio.create(UserPreferences.class);
            doReturn(saved).when(service).updatePreferences(memberId, request);

            // when
            var result = delegate.updatePreferences(householdId, memberId, request);

            // then
            verify(service).updatePreferences(memberId, request);
            assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(result.getBody()).isEqualTo(saved);
        }

        @Test
        void memberNotFound_throwsMemberNotFoundException() {
            // given
            var householdId = UUID.randomUUID();
            var memberId = UUID.randomUUID();
            var request = Instancio.create(UserPreferences.class);
            doThrow(new MemberNotFoundException()).when(service).updatePreferences(memberId, request);

            // when / then
            assertThatThrownBy(() -> delegate.updatePreferences(householdId, memberId, request))
                    .isInstanceOf(MemberNotFoundException.class);
        }
    }
}
