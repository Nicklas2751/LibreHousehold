package eu.wiegandt.librehousehold.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.FactorGrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

@ExtendWith(MockitoExtension.class)
class SessionEstablisherImplTest {

    @InjectMocks
    SessionEstablisherImpl sessionEstablisher;

    @Mock
    UserDetailsService userDetailsService;

    @Mock
    HttpServletRequest request;

    @Mock
    HttpServletResponse httpServletResponse;

    @Nested
    class establishSession {

        @Test
        void validEmail_setsAuthenticatedSecurityContextWithPasswordAuthority() {
            // given
            var username = "account-id";
            var email = "max@example.com";
            var userDetails = new User(username, "hash", List.of(new SimpleGrantedAuthority("ROLE_USER")));
            doReturn(userDetails).when(userDetailsService).loadUserByUsername(email);

            // when
            sessionEstablisher.establishSession(email);

            // then
            var authentication = SecurityContextHolder.getContext().getAuthentication();
            assertThat(authentication)
                    .extracting(Authentication::isAuthenticated, Authentication::getName,
                            a -> a.getAuthorities().stream()
                                    .map(GrantedAuthority::getAuthority)
                                    .anyMatch(FactorGrantedAuthority.PASSWORD_AUTHORITY::equals))
                    .containsExactly(true, username, true);
        }
    }
}
