package eu.wiegandt.librehousehold.statistics.controller;

import eu.wiegandt.librehousehold.expenses.ExpenseStatisticsProvider;
import eu.wiegandt.librehousehold.household.HouseholdQuery;
import eu.wiegandt.librehousehold.model.ExpenseStatsByCategory;
import eu.wiegandt.librehousehold.model.ExpenseStatsByMember;
import eu.wiegandt.librehousehold.model.StatisticsPeriod;
import eu.wiegandt.librehousehold.model.StatisticsResponse;
import eu.wiegandt.librehousehold.model.TaskStatsByMember;
import eu.wiegandt.librehousehold.statistics.StatisticsPeriodConverter;
import eu.wiegandt.librehousehold.statistics.exception.HouseholdNotFoundException;
import eu.wiegandt.librehousehold.tasks.TaskStatisticsProvider;
import org.instancio.Instancio;
import org.instancio.junit.InstancioExtension;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;

@ExtendWith({MockitoExtension.class, InstancioExtension.class})
class StatisticsApiDelegateImplTest {

    @Mock
    private HouseholdQuery householdQuery;

    @Mock
    private ExpenseStatisticsProvider expenseStatisticsProvider;

    @Mock
    private TaskStatisticsProvider taskStatisticsProvider;

    @InjectMocks
    private StatisticsApiDelegateImpl delegate;

    @Nested
    class getStatistics {

        @Test
        void unknownHousehold_throwsHouseholdNotFoundException() {
            // given
            var householdId = UUID.randomUUID();
            doReturn(false).when(householdQuery).householdExists(householdId);

            // when / then
            assertThatThrownBy(() -> delegate.getStatistics(householdId, "THIS_MONTH"))
                    .isInstanceOf(HouseholdNotFoundException.class);
        }

        @Test
        void validRequest_returns200WithSortedAndAssembledResponse() {
            // given
            var householdId = UUID.randomUUID();
            var period = StatisticsPeriod.LAST_3_MONTHS;
            var totalExpenses = BigDecimal.valueOf(300.0);
            var avgExpensesPerMonth = BigDecimal.valueOf(100.0);
            var categories = Instancio.ofList(ExpenseStatsByCategory.class).size(3).create();
            var members = Instancio.ofList(ExpenseStatsByMember.class).size(2).create();
            var tasks = Instancio.ofList(TaskStatsByMember.class).size(2).create();
            doReturn(true).when(householdQuery).householdExists(householdId);
            doReturn(totalExpenses).when(expenseStatisticsProvider).getTotalExpenses(eq(householdId), any(), any());
            doReturn(avgExpensesPerMonth).when(expenseStatisticsProvider).getAvgExpensesPerMonth(eq(householdId), any(), any());
            doReturn(categories).when(expenseStatisticsProvider).getExpenseStatsByCategory(eq(householdId), any(), any());
            doReturn(members).when(expenseStatisticsProvider).getExpenseStatsByMember(eq(householdId), any(), any());
            doReturn(tasks).when(taskStatisticsProvider).getTaskStatsByMember(householdId);
            var range = StatisticsPeriodConverter.convert(period, LocalDate.now());
            var expected = new StatisticsResponse(
                    period,
                    range.from(),
                    range.to(),
                    totalExpenses.doubleValue(),
                    avgExpensesPerMonth.doubleValue(),
                    categories.stream().sorted(Comparator.comparingDouble(ExpenseStatsByCategory::getTotal).reversed()).toList(),
                    members.stream().sorted(Comparator.comparingDouble(ExpenseStatsByMember::getTotal).reversed()).toList(),
                    tasks.stream().sorted(Comparator.comparingInt(TaskStatsByMember::getDone).reversed()).toList());

            // when
            var result = delegate.getStatistics(householdId, period.getValue());

            // then
            assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(result.getBody()).usingRecursiveComparison().isEqualTo(expected);
        }
    }
}
