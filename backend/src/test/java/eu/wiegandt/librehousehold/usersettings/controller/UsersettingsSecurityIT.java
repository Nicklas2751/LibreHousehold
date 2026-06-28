package eu.wiegandt.librehousehold.usersettings.controller;

import eu.wiegandt.librehousehold.api.UsersettingsApiController;
import eu.wiegandt.librehousehold.auth.MethodSecurityTestConfig;
import eu.wiegandt.librehousehold.core.ResourceOwnerQuery;
import eu.wiegandt.librehousehold.usersettings.service.UsersettingsService;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = UsersettingsApiController.class)
@Import({UsersettingsApiDelegateImpl.class, MethodSecurityTestConfig.class})
@ImportAutoConfiguration(JacksonAutoConfiguration.class)
class UsersettingsSecurityIT {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    UsersettingsService usersettingsService;

    @MockitoBean
    ResourceOwnerQuery resourceOwnerQuery;

    @Nested
    class deleteAccount {

        @Test
        void withoutToken_returns401() throws Exception {
            // given / when / then
            mockMvc.perform(delete("/v1/household/{householdId}/members/{memberId}/account",
                            UUID.randomUUID(), UUID.randomUUID())
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void withDifferentAccountId_returns403() throws Exception {
            // given
            var householdId = UUID.randomUUID();
            var memberId = UUID.randomUUID();
            doReturn(false).when(resourceOwnerQuery).isOwner(any(), any());

            // when / then
            mockMvc.perform(delete("/v1/household/{householdId}/members/{memberId}/account", householdId, memberId)
                            .with(jwt().jwt(b -> b
                                    .subject(UUID.randomUUID().toString())
                                    .claim("household_id", householdId.toString()))))
                    .andExpect(status().isForbidden());
        }

        @Test
        void withMatchingAccountId_returns204() throws Exception {
            // given
            var householdId = UUID.randomUUID();
            var memberId = UUID.randomUUID();
            doReturn(true).when(resourceOwnerQuery).isOwner(any(), any());

            // when / then
            mockMvc.perform(delete("/v1/household/{householdId}/members/{memberId}/account", householdId, memberId)
                            .with(jwt().jwt(b -> b
                                    .subject(memberId.toString())
                                    .claim("household_id", householdId.toString()))))
                    .andExpect(status().isNoContent());
        }
    }
}
