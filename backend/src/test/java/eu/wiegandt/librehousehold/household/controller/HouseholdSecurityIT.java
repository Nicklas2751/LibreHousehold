package eu.wiegandt.librehousehold.household.controller;

import eu.wiegandt.librehousehold.api.HouseholdApiController;
import eu.wiegandt.librehousehold.auth.MethodSecurityTestConfig;
import eu.wiegandt.librehousehold.household.service.HouseholdManagementService;
import eu.wiegandt.librehousehold.household.service.HouseholdSetupService;
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

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = HouseholdApiController.class)
@Import({HouseholdApiDelegateImpl.class, MethodSecurityTestConfig.class})
@ImportAutoConfiguration(JacksonAutoConfiguration.class)
class HouseholdSecurityIT {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    HouseholdManagementService householdManagementService;

    @MockitoBean
    HouseholdSetupService householdSetupService;

    @Nested
    class deleteHousehold {

        @Test
        void withoutToken_returns401() throws Exception {
            // given / when / then
            mockMvc.perform(delete("/v1/household/{householdId}", UUID.randomUUID())
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void withMemberRole_returns403() throws Exception {
            // given
            var householdId = UUID.randomUUID();

            // when / then
            mockMvc.perform(delete("/v1/household/{householdId}", householdId)
                            .with(jwt().jwt(b -> b
                                    .claim("household_id", householdId.toString())
                                    .claim("role", "member"))))
                    .andExpect(status().isForbidden());
        }

        @Test
        void withAdminRole_returns204() throws Exception {
            // given
            var householdId = UUID.randomUUID();

            // when / then
            mockMvc.perform(delete("/v1/household/{householdId}", householdId)
                            .with(jwt().jwt(b -> b
                                    .claim("household_id", householdId.toString())
                                    .claim("role", "admin"))))
                    .andExpect(status().isNoContent());
        }
    }

    @Nested
    class getInvite {

        @Test
        void withWrongHousehold_returns403() throws Exception {
            // given
            var householdId = UUID.randomUUID();
            var otherHouseholdId = UUID.randomUUID();

            // when / then
            mockMvc.perform(get("/v1/household/{householdId}/invite", householdId)
                            .with(jwt().jwt(b -> b
                                    .claim("household_id", otherHouseholdId.toString())
                                    .claim("role", "admin"))))
                    .andExpect(status().isForbidden());
        }
    }
}
