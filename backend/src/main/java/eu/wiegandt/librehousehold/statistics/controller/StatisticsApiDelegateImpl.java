package eu.wiegandt.librehousehold.statistics.controller;

import eu.wiegandt.librehousehold.api.StatisticsApiDelegate;
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
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Component
public class StatisticsApiDelegateImpl implements StatisticsApiDelegate {

    private final HouseholdQuery householdQuery;
    private final ExpenseStatisticsProvider expenseStatisticsProvider;
    private final TaskStatisticsProvider taskStatisticsProvider;

    public StatisticsApiDelegateImpl(HouseholdQuery householdQuery,
                                     ExpenseStatisticsProvider expenseStatisticsProvider,
                                     TaskStatisticsProvider taskStatisticsProvider) {
        this.householdQuery = householdQuery;
        this.expenseStatisticsProvider = expenseStatisticsProvider;
        this.taskStatisticsProvider = taskStatisticsProvider;
    }

    @Override
    public ResponseEntity<StatisticsResponse> getStatistics(UUID householdId, String period) {
        if (!householdQuery.householdExists(householdId)) {
            throw new HouseholdNotFoundException();
        }
        var statisticsPeriod = StatisticsPeriod.fromValue(period);
        var range = StatisticsPeriodConverter.convert(statisticsPeriod, LocalDate.now());

        var expensesByCategory = sortedByTotalDesc(
                expenseStatisticsProvider.getExpenseStatsByCategory(householdId, range.from(), range.to()));
        var expensesByMember = sortedByMemberTotalDesc(
                expenseStatisticsProvider.getExpenseStatsByMember(householdId, range.from(), range.to()));
        var tasksByMember = sortedByDoneDesc(
                taskStatisticsProvider.getTaskStatsByMember(householdId, range.from(), range.to()));

        var response = new StatisticsResponse(
                statisticsPeriod,
                range.from(),
                range.to(),
                expenseStatisticsProvider.getTotalExpenses(householdId, range.from(), range.to()).doubleValue(),
                expenseStatisticsProvider.getAvgExpensesPerMonth(householdId, range.from(), range.to()).doubleValue(),
                expensesByCategory,
                expensesByMember,
                tasksByMember);

        return ResponseEntity.ok(response);
    }

    private List<ExpenseStatsByCategory> sortedByTotalDesc(List<ExpenseStatsByCategory> stats) {
        return stats.stream()
                .sorted(Comparator.comparingDouble(ExpenseStatsByCategory::getTotal).reversed())
                .toList();
    }

    private List<ExpenseStatsByMember> sortedByMemberTotalDesc(List<ExpenseStatsByMember> stats) {
        return stats.stream()
                .sorted(Comparator.comparingDouble(ExpenseStatsByMember::getTotal).reversed())
                .toList();
    }

    private List<TaskStatsByMember> sortedByDoneDesc(List<TaskStatsByMember> stats) {
        return stats.stream()
                .sorted(Comparator.comparingInt(TaskStatsByMember::getDone).reversed())
                .toList();
    }
}
