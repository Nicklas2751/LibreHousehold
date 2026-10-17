package eu.wiegandt.librehousehold.config;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class LocalLoginDisabledFilterTest {

    private final LocalLoginDisabledFilter filter = new LocalLoginDisabledFilter();

    @Nested
    class doFilter {

        @Test
        void formLogin_redirectsToLoginWithLocalLoginDisabledReason() throws Exception {
            // given
            var response = new MockHttpServletResponse();

            // when
            filter.doFilter(new MockHttpServletRequest("POST", "/login"), response, new MockFilterChain());

            // then
            assertThat(response.getRedirectedUrl()).isEqualTo("/login?error&reason=local-login-disabled");
        }

        @Test
        void formLogin_doesNotReachFormLogin() throws Exception {
            // given
            var chain = new MockFilterChain();

            // when
            filter.doFilter(new MockHttpServletRequest("POST", "/login"), new MockHttpServletResponse(), chain);

            // then
            assertThat(chain.getRequest()).isNull();
        }

        @ParameterizedTest
        @CsvSource({"GET, /login", "POST, /logout", "POST, /v1/household/setup"})
        void otherRequest_passesThrough(String method, String path) throws Exception {
            // given
            var request = new MockHttpServletRequest(method, path);
            var chain = new MockFilterChain();

            // when
            filter.doFilter(request, new MockHttpServletResponse(), chain);

            // then
            assertThat(chain.getRequest()).isSameAs(request);
        }
    }
}
