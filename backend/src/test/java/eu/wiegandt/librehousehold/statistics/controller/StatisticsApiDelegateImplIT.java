package eu.wiegandt.librehousehold.statistics.controller;

import eu.wiegandt.librehousehold.api.StatisticsApiController;
import eu.wiegandt.librehousehold.expenses.ExpenseStatisticsProvider;
import eu.wiegandt.librehousehold.household.HouseholdQuery;
import eu.wiegandt.librehousehold.model.ExpenseStatsByCategory;
import eu.wiegandt.librehousehold.model.ExpenseStatsByMember;
import eu.wiegandt.librehousehold.model.TaskStatsByMember;
import eu.wiegandt.librehousehold.tasks.TaskStatisticsProvider;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = StatisticsApiController.class)
@Import(StatisticsApiDelegateImpl.class)
@ImportAutoConfiguration(JacksonAutoConfiguration.class)
class StatisticsApiDelegateImplIT {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private HouseholdQuery householdQuery;

    @MockitoBean
    private ExpenseStatisticsProvider expenseStatisticsProvider;

    @MockitoBean
    private TaskStatisticsProvider taskStatisticsProvider;

    @Nested
    class getStatistics {

        @Test
        void validRequest_returns200WithStatisticsResponse() throws Exception {
            // given
            var householdId = UUID.randomUUID();
            var catId = UUID.randomUUID();
            var memberId = UUID.randomUUID();
            doReturn(true).when(householdQuery).householdExists(householdId);
            doReturn(BigDecimal.valueOf(100.0)).when(expenseStatisticsProvider).getTotalExpenses(eq(householdId), any(), any());
            doReturn(BigDecimal.valueOf(100.0)).when(expenseStatisticsProvider).getAvgExpensesPerMonth(eq(householdId), any(), any());
            doReturn(List.of(new ExpenseStatsByCategory(catId, "Lebensmittel", 100.0, 100.0)))
                    .when(expenseStatisticsProvider).getExpenseStatsByCategory(eq(householdId), any(), any());
            doReturn(List.of(new ExpenseStatsByMember(memberId, "Alice", 100.0, 100.0)))
                    .when(expenseStatisticsProvider).getExpenseStatsByMember(eq(householdId), any(), any());
            doReturn(List.of(new TaskStatsByMember(memberId, "Alice", 5, 2)))
                    .when(taskStatisticsProvider).getTaskStatsByMember(eq(householdId), any(), any());

            // when / then
            mockMvc.perform(get("/v1/household/{householdId}/statistics", householdId)
                            .param("period", "THIS_MONTH"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.period").value("THIS_MONTH"))
                    .andExpect(jsonPath("$.totalExpenses").value(100.0))
                    .andExpect(jsonPath("$.expensesByCategory[0].categoryName").value("Lebensmittel"))
                    .andExpect(jsonPath("$.tasksByMember[0].memberName").value("Alice"));
        }

        @Test
        void unknownHousehold_returns404() throws Exception {
            // given
            var householdId = UUID.randomUUID();
            doReturn(false).when(householdQuery).householdExists(householdId);

            // when / then
            mockMvc.perform(get("/v1/household/{householdId}/statistics", householdId)
                            .param("period", "THIS_MONTH"))
                    .andExpect(status().isNotFound());
        }
    }
}
