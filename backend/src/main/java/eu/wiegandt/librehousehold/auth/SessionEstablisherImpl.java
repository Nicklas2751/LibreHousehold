package eu.wiegandt.librehousehold.auth;

import eu.wiegandt.librehousehold.core.SessionEstablishment;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.FactorGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Component;

import java.util.ArrayList;

@Component
class SessionEstablisherImpl implements SessionEstablishment {

    private final UserDetailsService userDetailsService;
    private final HttpServletRequest request;
    private final HttpServletResponse httpServletResponse;

    SessionEstablisherImpl(UserDetailsService userDetailsService,
                           HttpServletRequest request,
                           HttpServletResponse httpServletResponse) {
        this.userDetailsService = userDetailsService;
        this.request = request;
        this.httpServletResponse = httpServletResponse;
    }

    @Override
    public void establishSession(String email) {
        var userDetails = userDetailsService.loadUserByUsername(email);
        var authorities = new ArrayList<GrantedAuthority>(userDetails.getAuthorities());
        // Spring AS 7.1 reads auth_time from FactorGrantedAuthority.getIssuedAt()
        authorities.add(FactorGrantedAuthority.fromAuthority(FactorGrantedAuthority.PASSWORD_AUTHORITY));
        var authentication = UsernamePasswordAuthenticationToken.authenticated(
                userDetails, null, authorities);
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        new HttpSessionSecurityContextRepository().saveContext(context, request, httpServletResponse);
    }
}
