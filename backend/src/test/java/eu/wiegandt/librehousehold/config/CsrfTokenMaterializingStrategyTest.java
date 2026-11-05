package eu.wiegandt.librehousehold.config;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;

import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

class CsrfTokenMaterializingStrategyTest {

    private static final String XSRF_COOKIE = "XSRF-TOKEN";

    @Nested
    class onAuthentication {

        @Test
        void deferredToken_responseSetsXsrfCookie() {
            // given
            var request = new MockHttpServletRequest();
            var response = new MockHttpServletResponse();
            var deferredToken = CookieCsrfTokenRepository.withHttpOnlyFalse().loadDeferredToken(request, response);
            request.setAttribute(CsrfToken.class.getName(), new DeferredCsrfTokenAttribute(deferredToken::get));

            // when
            new CsrfTokenMaterializingStrategy().onAuthentication(new TestingAuthenticationToken("member", null),
                    request, response);

            // then
            assertThat(response.getCookie(XSRF_COOKIE)).isNotNull();
        }

        @Test
        void noTokenAttribute_noCookie() {
            // given
            var response = new MockHttpServletResponse();

            // when
            new CsrfTokenMaterializingStrategy().onAuthentication(new TestingAuthenticationToken("member", null),
                    new MockHttpServletRequest(), response);

            // then
            assertThat(response.getCookie(XSRF_COOKIE)).isNull();
        }
    }

    // Mirrors the supplier-backed token Spring Security puts into the request attribute
    private record DeferredCsrfTokenAttribute(Supplier<CsrfToken> delegate) implements CsrfToken {

        @Override
        public String getHeaderName() {
            return delegate.get().getHeaderName();
        }

        @Override
        public String getParameterName() {
            return delegate.get().getParameterName();
        }

        @Override
        public String getToken() {
            return delegate.get().getToken();
        }
    }
}
