package eu.wiegandt.librehousehold.household;

import eu.wiegandt.librehousehold.api.HouseholdApiController;
import eu.wiegandt.librehousehold.model.Household;
import eu.wiegandt.librehousehold.model.HouseholdSetup;
import eu.wiegandt.librehousehold.model.HouseholdUpdate;
import eu.wiegandt.librehousehold.model.Member;
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
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = HouseholdApiController.class)
@Import(HouseholdApiDelegateImpl.class)
@ImportAutoConfiguration(JacksonAutoConfiguration.class)
class HouseholdValidationIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private HouseholdSetupService householdSetupService;

    @MockitoBean
    private HouseholdManagementService householdManagementService;

    @Nested
    class setupHousehold {

        @Test
        void householdNameTooLong_returns400() throws Exception {
            // given
            var household = new Household(UUID.randomUUID(), "x".repeat(101));
            var member = new Member(UUID.randomUUID(), "Max Mustermann", "max@example.com");
            var setup = new HouseholdSetup(household, member);

            // when / then
            mockMvc.perform(post("/v1/household/setup")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(setup)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void householdNameTooShort_returns400() throws Exception {
            // given
            var household = new Household(UUID.randomUUID(), "ab");
            var member = new Member(UUID.randomUUID(), "Max Mustermann", "max@example.com");
            var setup = new HouseholdSetup(household, member);

            // when / then
            mockMvc.perform(post("/v1/household/setup")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(setup)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    class updateHousehold {

        @Test
        void nameTooLong_returns400() throws Exception {
            // given
            var update = new HouseholdUpdate("x".repeat(101));

            // when / then
            mockMvc.perform(patch("/v1/household/{householdId}", UUID.randomUUID())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(update)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void nameTooShort_returns400() throws Exception {
            // given
            var update = new HouseholdUpdate("ab");

            // when / then
            mockMvc.perform(patch("/v1/household/{householdId}", UUID.randomUUID())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(update)))
                    .andExpect(status().isBadRequest());
        }
    }
}
