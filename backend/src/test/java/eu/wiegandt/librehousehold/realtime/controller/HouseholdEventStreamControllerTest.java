package eu.wiegandt.librehousehold.realtime.controller;

import eu.wiegandt.librehousehold.household.AccountPrincipal;
import eu.wiegandt.librehousehold.realtime.config.RealtimeProperties;
import eu.wiegandt.librehousehold.realtime.model.StreamConnection;
import eu.wiegandt.librehousehold.realtime.service.HouseholdEventStreams;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;

import java.io.IOException;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class HouseholdEventStreamControllerTest {

    @Test
    void openStream_member_returnedEmitterRegisteredWithMemberAndSession() throws IOException {
        // given
        var streams = new HouseholdEventStreams();
        var controller = new HouseholdEventStreamController(streams,
                new RealtimeProperties(Duration.ofSeconds(5), Duration.ofMinutes(25)));
        var householdId = UUID.randomUUID();
        var memberId = UUID.randomUUID();
        var request = new MockHttpServletRequest();
        request.setSession(new MockHttpSession(null, "session-id"));

        // when
        var response = controller.openStream(householdId, AccountPrincipal.identifiedBy(memberId), request);

        // then
        assertThat(streams.connectionsOf(householdId))
                .extracting(StreamConnection::memberId, StreamConnection::sessionId, StreamConnection::emitter)
                .containsExactly(tuple(memberId, "session-id", response.getBody()));
    }

    @Test
    void openStream_configuredEmitterTimeout_emitterUsesTheTimeout() throws IOException {
        // given
        var emitterTimeout = Duration.ofMinutes(10);
        var controller = new HouseholdEventStreamController(new HouseholdEventStreams(),
                new RealtimeProperties(Duration.ofSeconds(5), emitterTimeout));
        var request = new MockHttpServletRequest();
        request.setSession(new MockHttpSession());

        // when
        var response = controller.openStream(UUID.randomUUID(), AccountPrincipal.identifiedBy(UUID.randomUUID()),
                request);

        // then
        assertThat(response.getBody().getTimeout()).isEqualTo(emitterTimeout.toMillis());
    }
}
