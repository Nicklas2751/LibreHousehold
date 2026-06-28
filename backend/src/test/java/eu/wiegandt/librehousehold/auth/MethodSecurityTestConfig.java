package eu.wiegandt.librehousehold.auth;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

import java.util.List;

@TestConfiguration
@EnableWebSecurity
@EnableMethodSecurity
public class MethodSecurityTestConfig {

    @Bean
    SecurityFilterChain testSecurityFilterChain(HttpSecurity http) {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                .oauth2ResourceServer(rs -> rs.jwt(Customizer.withDefaults()));
        return http.build();
    }

    @Bean
    JwtDecoder testJwtDecoder() {
        return token -> Jwt.withTokenValue(token).header("alg", "RS256").build();
    }

    @Bean
    HouseholdScopeChecker householdScopeChecker() {
        return new HouseholdScopeChecker();
    }

    @Bean
    AdminChecker adminChecker() {
        return new AdminChecker();
    }

    @Bean
    AuthorChecker authorChecker(List<ResourceOwnerQuery> ownerQueries) {
        return new AuthorChecker(ownerQueries);
    }
}
