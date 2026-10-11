package eu.wiegandt.librehousehold.config;

import eu.wiegandt.librehousehold.model.SocialLoginContext;
import eu.wiegandt.librehousehold.model.SocialLoginContext.ContextEnum;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SocialLoginContextStoreTest {

    private final SocialLoginContextStore store = new SocialLoginContextStore();

    @AfterEach
    void clearThreadLocalState() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Nested
    class consume {

        @Test
        void setupContextStored_returnsSetupContext() {
            // given
            bindRequest(new MockHttpServletRequest());
            store.store(new SocialLoginContext(ContextEnum.SETUP));

            // when
            var result = store.consume();

            // then
            assertThat(result).isEqualTo(new SocialLoginContext(ContextEnum.SETUP));
        }

        @Test
        void inviteContextWithToken_returnsTokenAlongsideContext() {
            // given
            var inviteContext = new SocialLoginContext(ContextEnum.INVITE).inviteToken(UUID.randomUUID());
            bindRequest(new MockHttpServletRequest());
            store.store(inviteContext);

            // when
            var result = store.consume();

            // then
            assertThat(result).isEqualTo(inviteContext);
        }

        @Test
        void noContextStored_returnsLoginDefault() {
            // given
            var request = new MockHttpServletRequest();
            request.getSession();
            bindRequest(request);

            // when
            var result = store.consume();

            // then
            assertThat(result).isEqualTo(new SocialLoginContext(ContextEnum.LOGIN));
        }

        @Test
        void noSession_returnsLoginDefault() {
            // given
            bindRequest(new MockHttpServletRequest());

            // when
            var result = store.consume();

            // then
            assertThat(result).isEqualTo(new SocialLoginContext(ContextEnum.LOGIN));
        }

        @Test
        void contextConsumedBefore_returnsLoginDefault() {
            // given
            bindRequest(new MockHttpServletRequest());
            store.store(new SocialLoginContext(ContextEnum.SETUP));
            store.consume();

            // when
            var result = store.consume();

            // then
            assertThat(result).isEqualTo(new SocialLoginContext(ContextEnum.LOGIN));
        }
    }

    private void bindRequest(MockHttpServletRequest request) {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request, new MockHttpServletResponse()));
    }
}
