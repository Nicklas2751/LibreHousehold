package eu.wiegandt.librehousehold.auth;

import eu.wiegandt.librehousehold.api.AuthApiController;
import eu.wiegandt.librehousehold.model.AuthProviders;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AuthApiController.class)
@Import({AuthApiDelegateImpl.class, AuthProviderControllerIT.SecurityConfig.class})
@ImportAutoConfiguration(JacksonAutoConfiguration.class)
class AuthProviderControllerIT {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    AuthProviderService authProviderService;

    @TestConfiguration
    @EnableWebSecurity
    @EnableMethodSecurity
    static class SecurityConfig {

        @Bean
        SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception {
            http
                    .csrf(AbstractHttpConfigurer::disable)
                    .authorizeHttpRequests(auth -> auth
                            .requestMatchers("/v1/auth/providers").permitAll()
                            .anyRequest().authenticated());
            return http.build();
        }
    }

    @Nested
    class getAuthProviders {

        @Test
        void withoutToken_returns200() throws Exception {
            // given
            doReturn(new AuthProviders().local(true).socialProviders(List.of()))
                    .when(authProviderService).getAvailableProviders();

            // when / then
            mockMvc.perform(get("/v1/auth/providers"))
                    .andExpect(status().isOk());
        }

        @Test
        void withLocalOnlyConfig_responseContainsLocalTrueAndEmptySocialProviders() throws Exception {
            // given
            doReturn(new AuthProviders().local(true).socialProviders(List.of()))
                    .when(authProviderService).getAvailableProviders();

            // when / then
            mockMvc.perform(get("/v1/auth/providers"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.local").value(true))
                    .andExpect(jsonPath("$.socialProviders").isArray())
                    .andExpect(jsonPath("$.socialProviders").isEmpty());
        }

        @Test
        void withSocialProviders_responseContainsProviderIds() throws Exception {
            // given
            doReturn(new AuthProviders().local(true).socialProviders(List.of("github", "keycloak")))
                    .when(authProviderService).getAvailableProviders();

            // when / then
            mockMvc.perform(get("/v1/auth/providers"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.socialProviders[0]").value("github"))
                    .andExpect(jsonPath("$.socialProviders[1]").value("keycloak"));
        }
    }
}
