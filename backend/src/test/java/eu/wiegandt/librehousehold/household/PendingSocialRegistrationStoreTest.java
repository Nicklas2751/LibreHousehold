package eu.wiegandt.librehousehold.household;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.assertj.core.api.Assertions.assertThat;

class PendingSocialRegistrationStoreTest {

    private final PendingSocialRegistrationStore store = new PendingSocialRegistrationStore();

    @AfterEach
    void clearThreadLocalState() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Nested
    class find {

        @Test
        void identityStored_returnsStoredIdentity() {
            // given
            var identity = new ExternalIdentity("google", "external-subject", "max@example.com", "Max Mustermann");
            bindRequest(new MockHttpServletRequest());
            store.store(identity);

            // when
            var result = store.find();

            // then
            assertThat(result).contains(identity);
        }

        @Test
        void identityFoundBefore_returnsIdentityAgain() {
            // given
            var identity = new ExternalIdentity("google", "external-subject", "max@example.com", null);
            bindRequest(new MockHttpServletRequest());
            store.store(identity);
            store.find();

            // when
            var result = store.find();

            // then
            assertThat(result).contains(identity);
        }

        @Test
        void sessionWithoutIdentity_returnsEmpty() {
            // given
            var request = new MockHttpServletRequest();
            request.getSession();
            bindRequest(request);

            // when
            var result = store.find();

            // then
            assertThat(result).isEmpty();
        }

        @Test
        void noSession_returnsEmptyWithoutCreatingSession() {
            // given
            var request = new MockHttpServletRequest();
            bindRequest(request);

            // when
            store.find();

            // then
            assertThat(request.getSession(false)).isNull();
        }

        @Test
        void identityConsumedBefore_returnsEmpty() {
            // given
            bindRequest(new MockHttpServletRequest());
            store.store(new ExternalIdentity("google", "external-subject", "max@example.com", null));
            store.consume();

            // when
            var result = store.find();

            // then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    class consume {

        @Test
        void noSession_doesNotCreateSession() {
            // given
            var request = new MockHttpServletRequest();
            bindRequest(request);

            // when
            store.consume();

            // then
            assertThat(request.getSession(false)).isNull();
        }
    }

    private void bindRequest(MockHttpServletRequest request) {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request, new MockHttpServletResponse()));
    }
}
