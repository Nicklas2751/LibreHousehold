package eu.wiegandt.librehousehold.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RevokeAuthorizedClientLogoutHandlerTest {

    @Mock
    private OAuth2AuthorizedClientService authorizedClientService;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private RevokeAuthorizedClientLogoutHandler logoutHandler;

    @Test
    void logout_authenticatedSession_revokesAuthorizedClient() {
        // given
        var principalName = "max@example.com";
        doReturn(principalName).when(authentication).getName();

        // when
        logoutHandler.logout(request, response, authentication);

        // then
        verify(authorizedClientService).removeAuthorizedClient(RegisteredClientSeeder.CLIENT_ID, principalName);
    }
}
