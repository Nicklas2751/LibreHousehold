package eu.wiegandt.librehousehold.household.service;

import eu.wiegandt.librehousehold.household.AccountPrincipal;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class MemberSessionsTest {

    @Mock
    private SessionRegistry sessionRegistry;

    @InjectMocks
    private MemberSessions memberSessions;

    @Nested
    class expireAll {

        @Test
        void existingSessions_expired() {
            // given
            var memberId = UUID.randomUUID();
            var sessions = List.of(newSession(memberId, "session-1"), newSession(memberId, "session-2"));
            doReturn(sessions).when(sessionRegistry).getAllSessions(AccountPrincipal.identifiedBy(memberId), false);

            // when
            memberSessions.expireAll(memberId);

            // then
            assertThat(sessions).allMatch(SessionInformation::isExpired);
        }

        @Test
        void noSessions_doesNotThrow() {
            // given
            var memberId = UUID.randomUUID();
            doReturn(List.of()).when(sessionRegistry).getAllSessions(AccountPrincipal.identifiedBy(memberId), false);

            // when / then
            assertThatCode(() -> memberSessions.expireAll(memberId)).doesNotThrowAnyException();
        }

        @Test
        void multipleMembers_expiresSessionsOfEveryMember() {
            // given
            var firstMemberId = UUID.randomUUID();
            var secondMemberId = UUID.randomUUID();
            var firstSession = newSession(firstMemberId, "session-1");
            var secondSession = newSession(secondMemberId, "session-2");
            doReturn(List.of(firstSession)).when(sessionRegistry)
                    .getAllSessions(AccountPrincipal.identifiedBy(firstMemberId), false);
            doReturn(List.of(secondSession)).when(sessionRegistry)
                    .getAllSessions(AccountPrincipal.identifiedBy(secondMemberId), false);

            // when
            memberSessions.expireAll(List.of(firstMemberId, secondMemberId));

            // then
            assertThat(List.of(firstSession, secondSession)).allMatch(SessionInformation::isExpired);
        }

        @Test
        void noMembers_noInteraction() {
            // when
            memberSessions.expireAll(List.of());

            // then
            verifyNoInteractions(sessionRegistry);
        }
    }

    private SessionInformation newSession(UUID memberId, String sessionId) {
        return new SessionInformation(AccountPrincipal.identifiedBy(memberId), sessionId, Date.from(Instant.now()));
    }
}
