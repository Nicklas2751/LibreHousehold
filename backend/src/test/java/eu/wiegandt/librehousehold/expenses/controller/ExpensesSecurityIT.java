package eu.wiegandt.librehousehold.expenses.controller;

import eu.wiegandt.librehousehold.api.ExpensesApiController;
import eu.wiegandt.librehousehold.auth.MethodSecurityTestConfig;
import eu.wiegandt.librehousehold.core.ResourceOwnerQuery;
import eu.wiegandt.librehousehold.expenses.service.CategoryService;
import eu.wiegandt.librehousehold.expenses.service.ExpenseService;
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

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ExpensesApiController.class)
@Import({ExpensesApiDelegateImpl.class, MethodSecurityTestConfig.class})
@ImportAutoConfiguration(JacksonAutoConfiguration.class)
class ExpensesSecurityIT {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    ExpenseService expenseService;

    @MockitoBean
    CategoryService categoryService;

    @MockitoBean
    ResourceOwnerQuery resourceOwnerQuery;

    @Nested
    class getExpenses {

        @Test
        void withoutToken_returns401() throws Exception {
            // given / when / then
            mockMvc.perform(get("/v1/household/{householdId}/expenses", UUID.randomUUID())
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void withWrongHousehold_returns403() throws Exception {
            // given
            var householdId = UUID.randomUUID();
            var otherHouseholdId = UUID.randomUUID();

            // when / then
            mockMvc.perform(get("/v1/household/{householdId}/expenses", householdId)
                            .with(jwt().jwt(b -> b.claim("household_id", otherHouseholdId.toString()))))
                    .andExpect(status().isForbidden());
        }

        @Test
        void withCorrectHousehold_returns200() throws Exception {
            // given
            var householdId = UUID.randomUUID();
            doReturn(List.of()).when(expenseService).getExpenses(householdId);

            // when / then
            mockMvc.perform(get("/v1/household/{householdId}/expenses", householdId)
                            .with(jwt().jwt(b -> b.claim("household_id", householdId.toString()))))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    class deleteExpense {

        @Test
        void withNonOwner_returns403() throws Exception {
            // given
            var householdId = UUID.randomUUID();
            var expenseId = UUID.randomUUID();
            doReturn(false).when(resourceOwnerQuery).isOwner(any(), any());

            // when / then
            mockMvc.perform(delete("/v1/household/{householdId}/expenses/{expenseId}", householdId, expenseId)
                            .with(jwt().jwt(b -> b
                                    .subject(UUID.randomUUID().toString())
                                    .claim("household_id", householdId.toString()))))
                    .andExpect(status().isForbidden());
        }

        @Test
        void withOwner_returns204() throws Exception {
            // given
            var householdId = UUID.randomUUID();
            var expenseId = UUID.randomUUID();
            doReturn(true).when(resourceOwnerQuery).isOwner(any(), any());

            // when / then
            mockMvc.perform(delete("/v1/household/{householdId}/expenses/{expenseId}", householdId, expenseId)
                            .with(jwt().jwt(b -> b
                                    .subject(UUID.randomUUID().toString())
                                    .claim("household_id", householdId.toString()))))
                    .andExpect(status().isNoContent());
        }
    }
}
