package eu.wiegandt.librehousehold.config;

import eu.wiegandt.librehousehold.household.AccountLockedException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.net.URI;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ProblemDetailLoginFailureHandlerTest {

    private static final Instant LOCKED_UNTIL = Instant.parse("2099-11-03T18:45:00Z");

    @Mock
    private HandlerExceptionResolver handlerExceptionResolver;

    @InjectMocks
    private ProblemDetailLoginFailureHandler failureHandler;

    @Nested
    class onAuthenticationFailure {

        static List<Arguments> failures() {
            return List.of(
                    argumentSet("wrong credentials", new BadCredentialsException("Bad credentials"),
                            problem(HttpStatus.UNAUTHORIZED, "/problems/invalid-credentials", "Incorrect email or password.")),
                    argumentSet("unverified account", new DisabledException("User is disabled"),
                            problem(HttpStatus.FORBIDDEN, "/problems/email-not-verified",
                                    "This action requires a verified email address.")),
                    argumentSet("locked account", new AccountLockedException(LOCKED_UNTIL), lockedProblem()),
                    argumentSet("locked account as cause", new InternalAuthenticationServiceException("wrapped",
                            new AccountLockedException(LOCKED_UNTIL)), lockedProblem()),
                    argumentSet("authentication service failure",
                            new InternalAuthenticationServiceException("database down"),
                            ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR)),
                    argumentSet("other authentication service failure",
                            new AuthenticationServiceException("database down"),
                            ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR)));
        }

        @ParameterizedTest
        @MethodSource("failures")
        void anyFailure_resolvesExpectedProblem(AuthenticationException exception, ProblemDetail expected) {
            // given
            var request = new MockHttpServletRequest();
            var response = new MockHttpServletResponse();
            var problem = ArgumentCaptor.forClass(ErrorResponseException.class);

            // when
            failureHandler.onAuthenticationFailure(request, response, exception);

            // then
            verify(handlerExceptionResolver).resolveException(eq(request), eq(response), isNull(), problem.capture());
            assertThat(problem.getValue().getBody()).usingRecursiveComparison().isEqualTo(expected);
        }

        @Test
        void lockedAccount_setsRetryAfter() {
            // given
            var lockedUntil = Instant.now().plusSeconds(900);
            var problem = ArgumentCaptor.forClass(ErrorResponseException.class);

            // when
            failureHandler.onAuthenticationFailure(new MockHttpServletRequest(), new MockHttpServletResponse(),
                    new AccountLockedException(lockedUntil));

            // then
            verify(handlerExceptionResolver).resolveException(
                    any(), any(), isNull(), problem.capture());
            assertThat(problem.getValue().getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("900");
        }
    }

    private static ProblemDetail problem(HttpStatus status, String type, String detail) {
        var problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create(type));
        return problem;
    }

    private static ProblemDetail lockedProblem() {
        var problem = problem(HttpStatus.FORBIDDEN, "/problems/account-locked",
                "This account is temporarily locked due to repeated failed login attempts.");
        problem.setProperty("lockedUntil", LOCKED_UNTIL);
        return problem;
    }
}
