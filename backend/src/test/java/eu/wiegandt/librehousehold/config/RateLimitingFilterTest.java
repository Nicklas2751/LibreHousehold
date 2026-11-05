package eu.wiegandt.librehousehold.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
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
    private HandlerExceptionResolver handlerExceptionResolver;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    @Nested
    class doFilterInternal {

        @Test
        void notLimited_continuesChain() throws Exception {
            // given
            var rule = new RateLimitRule(LOGIN_PATH_MATCHER, rateLimiter, HttpServletRequest::getRemoteAddr);
            var filter = new RateLimitingFilter(List.of(rule), handlerExceptionResolver);
            doReturn("/login").when(request).getRequestURI();
            doReturn("127.0.0.1").when(request).getRemoteAddr();
            doReturn(Optional.empty()).when(rateLimiter).acquire("127.0.0.1");

            // when
            filter.doFilter(request, response, filterChain);

            // then
            verify(filterChain).doFilter(request, response);
        }

        @Test
        void limited_resolvesTooManyRequestsProblem() throws Exception {
            // given
            var rule = new RateLimitRule(LOGIN_PATH_MATCHER, rateLimiter, HttpServletRequest::getRemoteAddr);
            var filter = new RateLimitingFilter(List.of(rule), handlerExceptionResolver);
            var waitTime = Duration.ofSeconds(30);
            var expected = new TooManyRequestsProblem(waitTime).getBody();
            doReturn("/login").when(request).getRequestURI();
            doReturn("127.0.0.1").when(request).getRemoteAddr();
            doReturn(Optional.of(waitTime)).when(rateLimiter).acquire("127.0.0.1");
            var problemCaptor = ArgumentCaptor.forClass(TooManyRequestsProblem.class);

            // when
            filter.doFilter(request, response, filterChain);

            // then
            verify(handlerExceptionResolver).resolveException(eq(request),
                    eq(response), isNull(), problemCaptor.capture());
            assertThat(problemCaptor.getValue().getBody()).isEqualTo(expected);
        }

        @Test
        void limited_setsRetryAfterRoundedUpToSeconds() throws Exception {
            // given
            var rule = new RateLimitRule(LOGIN_PATH_MATCHER, rateLimiter, HttpServletRequest::getRemoteAddr);
            var filter = new RateLimitingFilter(List.of(rule), handlerExceptionResolver);
            doReturn("/login").when(request).getRequestURI();
            doReturn("127.0.0.1").when(request).getRemoteAddr();
            doReturn(Optional.of(Duration.ofMillis(29_500))).when(rateLimiter).acquire("127.0.0.1");
            var problemCaptor = ArgumentCaptor.forClass(TooManyRequestsProblem.class);

            // when
            filter.doFilter(request, response, filterChain);

            // then
            verify(handlerExceptionResolver).resolveException(eq(request),
                    eq(response), isNull(), problemCaptor.capture());
            assertThat(problemCaptor.getValue().getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("30");
        }

        @Test
        void limited_stopsChain() throws Exception {
            // given
            var rule = new RateLimitRule(LOGIN_PATH_MATCHER, rateLimiter, HttpServletRequest::getRemoteAddr);
            var filter = new RateLimitingFilter(List.of(rule), handlerExceptionResolver);
            doReturn("/login").when(request).getRequestURI();
            doReturn("127.0.0.1").when(request).getRemoteAddr();
            doReturn(Optional.of(Duration.ofSeconds(30))).when(rateLimiter).acquire("127.0.0.1");

            // when
            filter.doFilter(request, response, filterChain);

            // then
            verify(filterChain, never()).doFilter(request, response);
        }

        @Test
        void nonMatchingRequest_continuesChainWithoutCheckingRateLimiter() throws Exception {
            // given
            var rule = new RateLimitRule(LOGIN_PATH_MATCHER, rateLimiter, HttpServletRequest::getRemoteAddr);
            var filter = new RateLimitingFilter(List.of(rule), handlerExceptionResolver);
            doReturn("/other").when(request).getRequestURI();

            // when
            filter.doFilter(request, response, filterChain);

            // then
            verify(filterChain).doFilter(request, response);
            verifyNoInteractions(rateLimiter);
        }
    }
}
