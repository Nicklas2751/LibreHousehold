package eu.wiegandt.librehousehold.notifications.realtime.controller;

import eu.wiegandt.librehousehold.household.AccountPrincipal;
import eu.wiegandt.librehousehold.notifications.realtime.config.RealtimeProperties;
import eu.wiegandt.librehousehold.notifications.realtime.model.StreamConnection;
import eu.wiegandt.librehousehold.notifications.realtime.service.HouseholdEventStreams;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;

import java.io.IOException;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class HouseholdEventStreamControllerTest {

    @Mock
    private HouseholdEventStreams streams;

    @Captor
    private ArgumentCaptor<StreamConnection> registeredConnection;

    @Test
    void openStream_member_returnedEmitterRegisteredWithMemberAndSession() throws IOException {
        // given
        var controller = new HouseholdEventStreamController(streams,
                new RealtimeProperties(Duration.ofSeconds(5), Duration.ofMinutes(25), 5, 10, 500,
                        Duration.ofSeconds(30)));
        var householdId = UUID.randomUUID();
        var memberId = UUID.randomUUID();
        var request = new MockHttpServletRequest();
        request.setSession(new MockHttpSession(null, "session-id"));

        // when
        var response = controller.openStream(householdId, AccountPrincipal.identifiedBy(memberId), request);

        // then
        verify(streams).register(registeredConnection.capture());
        assertThat(registeredConnection.getValue())
                .isEqualTo(new StreamConnection(householdId, memberId, "session-id", response.getBody()));
    }

    @Test
    void openStream_configuredEmitterTimeout_emitterUsesTheTimeout() throws IOException {
        // given
        var emitterTimeout = Duration.ofMinutes(10);
        var controller = new HouseholdEventStreamController(streams,
                new RealtimeProperties(Duration.ofSeconds(5), emitterTimeout, 5, 10, 500, Duration.ofSeconds(30)));
        var request = new MockHttpServletRequest();
        request.setSession(new MockHttpSession());

        // when
        var response = controller.openStream(UUID.randomUUID(), AccountPrincipal.identifiedBy(UUID.randomUUID()),
                request);

        // then
        assertThat(response.getBody().getTimeout()).isEqualTo(emitterTimeout.toMillis());
    }
}
