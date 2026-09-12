package eu.wiegandt.librehousehold.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.util.matcher.RequestMatcher;

import java.util.List;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class RateLimitingFilterTest {

    private static final RequestMatcher LOGIN_PATH_MATCHER = request -> "/login".equals(request.getRequestURI());

    @Mock
    private RateLimiter rateLimiter;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    @Nested
    class doFilterInternal {

        @Test
        void matchingRequestUnderThreshold_continuesFilterChain() throws Exception {
            // given
            var rule = new RateLimitRule(LOGIN_PATH_MATCHER, rateLimiter, HttpServletRequest::getRemoteAddr);
            var filter = new RateLimitingFilter(List.of(rule));
            doReturn("/login").when(request).getRequestURI();
            doReturn("127.0.0.1").when(request).getRemoteAddr();
            doReturn(false).when(rateLimiter).isRateLimited("127.0.0.1");

            // when
            filter.doFilter(request, response, filterChain);

            // then
            verify(filterChain).doFilter(request, response);
        }

        @Test
        void matchingRequestAtThreshold_respondsWithTooManyRequestsAndStopsChain() throws Exception {
            // given
            var rule = new RateLimitRule(LOGIN_PATH_MATCHER, rateLimiter, HttpServletRequest::getRemoteAddr);
            var filter = new RateLimitingFilter(List.of(rule));
            doReturn("/login").when(request).getRequestURI();
            doReturn("127.0.0.1").when(request).getRemoteAddr();
            doReturn(true).when(rateLimiter).isRateLimited("127.0.0.1");

            // when
            filter.doFilter(request, response, filterChain);

            // then
            verify(response).setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            verify(filterChain, never()).doFilter(request, response);
        }

        @Test
        void nonMatchingRequest_continuesFilterChainWithoutCheckingRateLimiter() throws Exception {
            // given
            var rule = new RateLimitRule(LOGIN_PATH_MATCHER, rateLimiter, HttpServletRequest::getRemoteAddr);
            var filter = new RateLimitingFilter(List.of(rule));
            doReturn("/other").when(request).getRequestURI();

            // when
            filter.doFilter(request, response, filterChain);

            // then
            verify(filterChain).doFilter(request, response);
            verifyNoInteractions(rateLimiter);
        }
    }
}
