package eu.wiegandt.librehousehold.statistics.controller;

import eu.wiegandt.librehousehold.api.StatisticsApiController;
import eu.wiegandt.librehousehold.auth.MethodSecurityTestConfig;
import eu.wiegandt.librehousehold.expenses.ExpenseStatisticsProvider;
import eu.wiegandt.librehousehold.household.HouseholdQuery;
import eu.wiegandt.librehousehold.tasks.TaskStatisticsProvider;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = StatisticsApiController.class)
@Import({StatisticsApiDelegateImpl.class, MethodSecurityTestConfig.class})
@ImportAutoConfiguration(JacksonAutoConfiguration.class)
class StatisticsSecurityIT {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    HouseholdQuery householdQuery;

    @MockitoBean
    ExpenseStatisticsProvider expenseStatisticsProvider;

    @MockitoBean
    TaskStatisticsProvider taskStatisticsProvider;

    @Nested
    class getStatistics {

        @Test
        void withoutToken_returns401() throws Exception {
            // given / when / then
            mockMvc.perform(get("/v1/household/{householdId}/statistics", UUID.randomUUID())
                            .param("period", "THIS_MONTH")
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void withWrongHousehold_returns403() throws Exception {
            // given
            var householdId = UUID.randomUUID();
            var otherHouseholdId = UUID.randomUUID();

            // when / then
            mockMvc.perform(get("/v1/household/{householdId}/statistics", householdId)
                            .param("period", "THIS_MONTH")
                            .with(jwt().jwt(b -> b.claim("household_id", otherHouseholdId.toString()))))
                    .andExpect(status().isForbidden());
        }
    }
}
