package eu.wiegandt.librehousehold.expenses;

import eu.wiegandt.librehousehold.household.MemberQuery;
import eu.wiegandt.librehousehold.model.ExpenseStatsByCategory;
import eu.wiegandt.librehousehold.model.ExpenseStatsByMember;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
class ExpenseStatisticsService implements ExpenseStatisticsProvider {

    private static final String UNKNOWN = "Unknown";

    private final ExpenseRepository expenseRepository;
    private final CategoryRepository categoryRepository;
    private final MemberQuery memberQuery;

    ExpenseStatisticsService(ExpenseRepository expenseRepository,
                              CategoryRepository categoryRepository,
                              MemberQuery memberQuery) {
        this.expenseRepository = expenseRepository;
        this.categoryRepository = categoryRepository;
        this.memberQuery = memberQuery;
    }

    @Override
    public BigDecimal getTotalExpenses(UUID householdId, LocalDate from, LocalDate to) {
        return filterByDate(expenseRepository.findByHouseholdId(householdId), from, to)
                .stream()
                .map(ExpenseEntity::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Override
    public BigDecimal getAvgExpensesPerMonth(UUID householdId, LocalDate from, LocalDate to) {
        var total = getTotalExpenses(householdId, from, to);
        long months = ChronoUnit.MONTHS.between(from, to) + 1;
        return total.divide(BigDecimal.valueOf(months), 2, RoundingMode.HALF_UP);
    }

    @Override
    public List<ExpenseStatsByCategory> getExpenseStatsByCategory(UUID householdId, LocalDate from, LocalDate to) {
        var expenses = filterByDate(expenseRepository.findByHouseholdId(householdId), from, to);
        var categories = categoryRepository.findByHouseholdId(householdId).stream()
                .collect(Collectors.toMap(CategoryEntity::getId, category -> category));
        long months = ChronoUnit.MONTHS.between(from, to) + 1;

        return expenses.stream()
                .collect(Collectors.groupingBy(ExpenseEntity::getCategoryId,
                        Collectors.reducing(BigDecimal.ZERO, ExpenseEntity::getAmount, BigDecimal::add)))
                .entrySet().stream()
                .map(entry -> toCategoryStats(entry.getKey(), entry.getValue(), categories, months))
                .toList();
    }

    @Override
    public List<ExpenseStatsByMember> getExpenseStatsByMember(UUID householdId, LocalDate from, LocalDate to) {
        var expenses = filterByDate(expenseRepository.findByHouseholdId(householdId), from, to);
        long months = ChronoUnit.MONTHS.between(from, to) + 1;

        var totalsPerMember = expenses.stream()
                .collect(Collectors.groupingBy(ExpenseEntity::getPaidBy,
                        Collectors.reducing(BigDecimal.ZERO, ExpenseEntity::getAmount, BigDecimal::add)));
        var memberNames = memberQuery.findMemberNamesByIds(totalsPerMember.keySet());

        return totalsPerMember.entrySet().stream()
                .map(entry -> toMemberStats(entry.getKey(), entry.getValue(), memberNames, months))
                .toList();
    }

    List<ExpenseEntity> filterByDate(List<ExpenseEntity> expenses, LocalDate from, LocalDate to) {
        return expenses.stream()
                .filter(expense -> !expense.getDate().isBefore(from) && !expense.getDate().isAfter(to))
                .toList();
    }

    private ExpenseStatsByCategory toCategoryStats(UUID categoryId, BigDecimal total,
                                                    Map<UUID, CategoryEntity> categories, long months) {
        var category = categories.get(categoryId);
        var totalAsDouble = total.doubleValue();
        var stat = new ExpenseStatsByCategory(categoryId,
                category != null ? category.getName() : UNKNOWN, totalAsDouble, totalAsDouble / months);
        if (category != null && category.getIcon() != null) {
            stat.categoryIcon(category.getIcon());
        }
        return stat;
    }

    private ExpenseStatsByMember toMemberStats(UUID memberId, BigDecimal total,
                                                Map<UUID, String> memberNames, long months) {
        var totalAsDouble = total.doubleValue();
        return new ExpenseStatsByMember(memberId, memberNames.getOrDefault(memberId, UNKNOWN),
                totalAsDouble, totalAsDouble / months);
    }
}
