package eu.wiegandt.librehousehold.household.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;

@ExtendWith(MockitoExtension.class)
class AccountSessionAuthenticatorTest {

    @Mock
    private AuthenticationManager authenticationManager;

    @AfterEach
    void clearThreadLocalState() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void authenticateAndPersistSession_validCredentials_setsAuthenticationResultInSecurityContextHolder() {
        // given
        var email = "member@example.com";
        var rawPassword = "s3cret!";
        var authenticationResult = UsernamePasswordAuthenticationToken.authenticated(email, rawPassword, null);
        doReturn(authenticationResult).when(authenticationManager)
                .authenticate(eq(UsernamePasswordAuthenticationToken.unauthenticated(email, rawPassword)));
        RequestContextHolder.setRequestAttributes(
                new ServletRequestAttributes(new MockHttpServletRequest(), new MockHttpServletResponse()));
        var authenticator = new AccountSessionAuthenticator(authenticationManager);

        // when
        authenticator.authenticateAndPersistSession(email, rawPassword);

        // then
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isEqualTo(authenticationResult);
    }

    @Test
    void authenticateAndPersistSession_validCredentials_persistsSecurityContextInHttpSession() {
        // given
        var email = "member@example.com";
        var rawPassword = "s3cret!";
        var authenticationResult = UsernamePasswordAuthenticationToken.authenticated(email, rawPassword, null);
        doReturn(authenticationResult).when(authenticationManager)
                .authenticate(eq(UsernamePasswordAuthenticationToken.unauthenticated(email, rawPassword)));
        var request = new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(
                new ServletRequestAttributes(request, new MockHttpServletResponse()));
        var authenticator = new AccountSessionAuthenticator(authenticationManager);

        // when
        authenticator.authenticateAndPersistSession(email, rawPassword);

        // then
        var persistedContext = request.getSession()
                .getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        assertThat(persistedContext).isEqualTo(SecurityContextHolder.getContext());
    }

    @Test
    void authenticateAndPersistSession_noHttpServletResponseAvailable_throwsIllegalStateException() {
        // given
        var email = "member@example.com";
        var rawPassword = "s3cret!";
        var authenticationResult = UsernamePasswordAuthenticationToken.authenticated(email, rawPassword, null);
        doReturn(authenticationResult).when(authenticationManager)
                .authenticate(eq(UsernamePasswordAuthenticationToken.unauthenticated(email, rawPassword)));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
        var authenticator = new AccountSessionAuthenticator(authenticationManager);

        // when / then
        assertThatIllegalStateException()
                .isThrownBy(() -> authenticator.authenticateAndPersistSession(email, rawPassword))
                .withMessage("No HttpServletResponse available for the current request");
    }
}
