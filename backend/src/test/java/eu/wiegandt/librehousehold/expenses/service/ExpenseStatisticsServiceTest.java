package eu.wiegandt.librehousehold.expenses.service;
import eu.wiegandt.librehousehold.expenses.exception.*;
import eu.wiegandt.librehousehold.expenses.mapper.*;
import eu.wiegandt.librehousehold.expenses.model.*;
import eu.wiegandt.librehousehold.expenses.repository.*;

import eu.wiegandt.librehousehold.household.MemberQuery;
import eu.wiegandt.librehousehold.model.ExpenseStatsByCategory;
import eu.wiegandt.librehousehold.model.ExpenseStatsByMember;
import org.instancio.Instancio;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.instancio.Select.field;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

@ExtendWith(MockitoExtension.class)
class ExpenseStatisticsServiceTest {

    @Mock
    private ExpenseRepository expenseRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private MemberQuery memberQuery;

    @InjectMocks
    private ExpenseStatisticsService statisticsService;

    private static final LocalDate FROM = LocalDate.of(2026, 1, 1);
    private static final LocalDate TO = LocalDate.of(2026, 3, 31);

    @Nested
    class getTotalExpenses {

        @Test
        void expensesInRange_returnsSum() {
            // given
            var householdId = UUID.randomUUID();
            var e1 = Instancio.of(ExpenseEntity.class)
                    .set(field(ExpenseEntity.class, "amount"), BigDecimal.valueOf(30.0))
                    .set(field(ExpenseEntity.class, "date"), LocalDate.of(2026, 1, 15))
                    .create();
            var e2 = Instancio.of(ExpenseEntity.class)
                    .set(field(ExpenseEntity.class, "amount"), BigDecimal.valueOf(70.0))
                    .set(field(ExpenseEntity.class, "date"), LocalDate.of(2026, 2, 10))
                    .create();
            var outside = Instancio.of(ExpenseEntity.class)
                    .set(field(ExpenseEntity.class, "amount"), BigDecimal.valueOf(100.0))
                    .set(field(ExpenseEntity.class, "date"), LocalDate.of(2025, 12, 31))
                    .create();
            doReturn(List.of(e1, e2, outside)).when(expenseRepository).findByHouseholdId(householdId);

            // when
            var result = statisticsService.getTotalExpenses(householdId, FROM, TO);

            // then
            assertThat(result).isEqualByComparingTo(BigDecimal.valueOf(100.0));
        }
    }

    @Nested
    class getAvgExpensesPerMonth {

        @Test
        void threeMonthPeriod_dividesByThree() {
            // given
            var householdId = UUID.randomUUID();
            var e1 = Instancio.of(ExpenseEntity.class)
                    .set(field(ExpenseEntity.class, "amount"), BigDecimal.valueOf(90.0))
                    .set(field(ExpenseEntity.class, "date"), LocalDate.of(2026, 2, 1))
                    .create();
            doReturn(List.of(e1)).when(expenseRepository).findByHouseholdId(householdId);

            // when
            var result = statisticsService.getAvgExpensesPerMonth(householdId, FROM, TO);

            // then
            assertThat(result).isEqualByComparingTo(BigDecimal.valueOf(30.0));
        }
    }

    @Nested
    class getExpenseStatsByCategory {

        @Test
        void groupsExpensesByCategory_returnsStats() {
            // given
            var householdId = UUID.randomUUID();
            var catA = UUID.randomUUID();
            var catB = UUID.randomUUID();
            var catEntityA = Instancio.of(CategoryEntity.class)
                    .set(field(CategoryEntity.class, "id"), catA)
                    .ignore(field(CategoryEntity.class, "icon"))
                    .create();
            var catEntityB = Instancio.of(CategoryEntity.class)
                    .set(field(CategoryEntity.class, "id"), catB)
                    .ignore(field(CategoryEntity.class, "icon"))
                    .create();
            var e1 = Instancio.of(ExpenseEntity.class)
                    .set(field(ExpenseEntity.class, "amount"), BigDecimal.valueOf(30.0))
                    .set(field(ExpenseEntity.class, "date"), LocalDate.of(2026, 1, 10))
                    .set(field(ExpenseEntity.class, "categoryId"), catA)
                    .create();
            var e2 = Instancio.of(ExpenseEntity.class)
                    .set(field(ExpenseEntity.class, "amount"), BigDecimal.valueOf(60.0))
                    .set(field(ExpenseEntity.class, "date"), LocalDate.of(2026, 2, 10))
                    .set(field(ExpenseEntity.class, "categoryId"), catA)
                    .create();
            var e3 = Instancio.of(ExpenseEntity.class)
                    .set(field(ExpenseEntity.class, "amount"), BigDecimal.valueOf(45.0))
                    .set(field(ExpenseEntity.class, "date"), LocalDate.of(2026, 1, 20))
                    .set(field(ExpenseEntity.class, "categoryId"), catB)
                    .create();
            doReturn(List.of(e1, e2, e3)).when(expenseRepository).findByHouseholdId(householdId);
            doReturn(List.of(catEntityA, catEntityB)).when(categoryRepository).findByHouseholdId(householdId);
            var expectedA = new ExpenseStatsByCategory(catA, catEntityA.getName(), 90.0, 30.0);
            var expectedB = new ExpenseStatsByCategory(catB, catEntityB.getName(), 45.0, 15.0);

            // when
            var result = statisticsService.getExpenseStatsByCategory(householdId, FROM, TO);

            // then
            assertThat(result).usingRecursiveComparison()
                    .ignoringCollectionOrder()
                    .isEqualTo(List.of(expectedA, expectedB));
        }
    }

    @Nested
    class getExpenseStatsByMember {

        @Test
        void groupsExpensesByMember_returnsStats() {
            // given
            var householdId = UUID.randomUUID();
            var memberA = UUID.randomUUID();
            var memberB = UUID.randomUUID();
            var e1 = Instancio.of(ExpenseEntity.class)
                    .set(field(ExpenseEntity.class, "amount"), BigDecimal.valueOf(50.0))
                    .set(field(ExpenseEntity.class, "date"), LocalDate.of(2026, 1, 10))
                    .set(field(ExpenseEntity.class, "paidBy"), memberA)
                    .create();
            var e2 = Instancio.of(ExpenseEntity.class)
                    .set(field(ExpenseEntity.class, "amount"), BigDecimal.valueOf(40.0))
                    .set(field(ExpenseEntity.class, "date"), LocalDate.of(2026, 2, 5))
                    .set(field(ExpenseEntity.class, "paidBy"), memberB)
                    .create();
            doReturn(List.of(e1, e2)).when(expenseRepository).findByHouseholdId(householdId);
            doReturn(Map.of(memberA, "Alice", memberB, "Bob")).when(memberQuery).findMemberNamesByIds(any());
            var expectedA = new ExpenseStatsByMember(memberA, "Alice", 50.0, 50.0 / 3);
            var expectedB = new ExpenseStatsByMember(memberB, "Bob", 40.0, 40.0 / 3);

            // when
            var result = statisticsService.getExpenseStatsByMember(householdId, FROM, TO);

            // then
            assertThat(result).usingRecursiveComparison()
                    .ignoringCollectionOrder()
                    .isEqualTo(List.of(expectedA, expectedB));
        }
    }

    @Nested
    class filterByDate {

        @Test
        void expensesInRange_returnsOnlyMatchingExpenses() {
            // given
            var insideRange = Instancio.of(ExpenseEntity.class)
                    .set(field(ExpenseEntity.class, "date"), LocalDate.of(2026, 1, 15))
                    .create();
            var onStartBoundary = Instancio.of(ExpenseEntity.class)
                    .set(field(ExpenseEntity.class, "date"), FROM)
                    .create();
            var onEndBoundary = Instancio.of(ExpenseEntity.class)
                    .set(field(ExpenseEntity.class, "date"), TO)
                    .create();
            var beforeRange = Instancio.of(ExpenseEntity.class)
                    .set(field(ExpenseEntity.class, "date"), LocalDate.of(2025, 12, 31))
                    .create();
            var afterRange = Instancio.of(ExpenseEntity.class)
                    .set(field(ExpenseEntity.class, "date"), LocalDate.of(2026, 4, 1))
                    .create();

            // when
            var result = statisticsService.filterByDate(
                    List.of(insideRange, onStartBoundary, onEndBoundary, beforeRange, afterRange), FROM, TO);

            // then
            assertThat(result).containsExactlyInAnyOrder(insideRange, onStartBoundary, onEndBoundary);
        }

        @Test
        void noExpensesInRange_returnsEmptyList() {
            // given
            var outside = Instancio.of(ExpenseEntity.class)
                    .set(field(ExpenseEntity.class, "date"), LocalDate.of(2025, 6, 1))
                    .create();

            // when
            var result = statisticsService.filterByDate(List.of(outside), FROM, TO);

            // then
            assertThat(result).isEmpty();
        }
    }
}
