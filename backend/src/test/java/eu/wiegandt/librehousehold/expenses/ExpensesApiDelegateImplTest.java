package eu.wiegandt.librehousehold.expenses;

import eu.wiegandt.librehousehold.model.Category;
import eu.wiegandt.librehousehold.model.Expense;
import eu.wiegandt.librehousehold.model.ExpenseUpdate;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ExpensesApiDelegateImplTest {

    @Mock
    private CategoryService categoryService;

    @Mock
    private ExpenseService expenseService;

    @InjectMocks
    private ExpensesApiDelegateImpl delegate;

    @Nested
    class getCategories {

        @Test
        void validHousehold_returnsOk() {
            // given
            var householdId = UUID.randomUUID();
            var category = new Category(UUID.randomUUID(), "Groceries");
            doReturn(List.of(category)).when(categoryService).getCategories(householdId);

            // when
            var result = delegate.getCategories(householdId);

            // then
            assertThat(result.getStatusCode().value()).isEqualTo(200);
            assertThat(result.getBody()).containsExactly(category);
        }
    }

    @Nested
    class createCategory {

        @Test
        void validInput_returnsOk() {
            // given
            var householdId = UUID.randomUUID();
            var category = new Category(UUID.randomUUID(), "Utilities");
            doReturn(category).when(categoryService).createCategory(householdId, category);

            // when
            var result = delegate.createCategory(householdId, Optional.of(category));

            // then
            assertThat(result.getStatusCode().value()).isEqualTo(200);
            assertThat(result.getBody()).isEqualTo(category);
        }

        @Test
        void duplicate_throwsCategoryAlreadyExistsException() {
            // given
            var householdId = UUID.randomUUID();
            var category = new Category(UUID.randomUUID(), "Groceries");
            doThrow(CategoryAlreadyExistsException.class).when(categoryService).createCategory(householdId, category);

            // when / then
            assertThatThrownBy(() -> delegate.createCategory(householdId, Optional.of(category)))
                    .isInstanceOf(CategoryAlreadyExistsException.class);
        }

        @Test
        void missingBody_throwsCategoryBodyIsRequiredException() {
            // when / then
            assertThatThrownBy(() -> delegate.createCategory(UUID.randomUUID(), Optional.empty()))
                    .isInstanceOf(CategoryBodyIsRequiredException.class);
        }
    }

    @Nested
    class getExpenses {

        @Test
        void validHousehold_returnsOk() {
            // given
            var householdId = UUID.randomUUID();
            var expense = new Expense(UUID.randomUUID(), "Groceries", 50.0,
                    UUID.randomUUID(), LocalDate.of(2026, 1, 15), UUID.randomUUID());
            doReturn(List.of(expense)).when(expenseService).getExpenses(householdId);

            // when
            var result = delegate.getExpenses(householdId);

            // then
            assertThat(result.getStatusCode().value()).isEqualTo(200);
            assertThat(result.getBody()).containsExactly(expense);
        }
    }

    @Nested
    class getExpense {

        @Test
        void validInput_returnsOk() {
            // given
            var householdId = UUID.randomUUID();
            var expenseId = UUID.randomUUID();
            var expense = new Expense(expenseId, "Groceries", 50.0,
                    UUID.randomUUID(), LocalDate.of(2026, 1, 15), UUID.randomUUID());
            doReturn(expense).when(expenseService).getExpense(householdId, expenseId);

            // when
            var result = delegate.getExpense(householdId, expenseId);

            // then
            assertThat(result.getStatusCode().value()).isEqualTo(200);
            assertThat(result.getBody()).isEqualTo(expense);
        }

        @Test
        void notFound_throwsExpenseNotFoundException() {
            // given
            var householdId = UUID.randomUUID();
            var expenseId = UUID.randomUUID();
            doThrow(ExpenseNotFoundException.class).when(expenseService).getExpense(householdId, expenseId);

            // when / then
            assertThatThrownBy(() -> delegate.getExpense(householdId, expenseId))
                    .isInstanceOf(ExpenseNotFoundException.class);
        }
    }

    @Nested
    class createExpense {

        @Test
        void validInput_returnsOk() {
            // given
            var householdId = UUID.randomUUID();
            var expense = new Expense(UUID.randomUUID(), "Groceries", 50.0,
                    UUID.randomUUID(), LocalDate.of(2026, 1, 15), UUID.randomUUID());
            doReturn(expense).when(expenseService).createExpense(householdId, expense);

            // when
            var result = delegate.createExpense(householdId, Optional.of(expense));

            // then
            assertThat(result.getStatusCode().value()).isEqualTo(200);
            assertThat(result.getBody()).isEqualTo(expense);
        }

        @Test
        void missingBody_throwsExpenseBodyIsRequiredException() {
            // when / then
            assertThatThrownBy(() -> delegate.createExpense(UUID.randomUUID(), Optional.empty()))
                    .isInstanceOf(ExpenseBodyIsRequiredException.class);
        }
    }

    @Nested
    class updateExpense {

        @Test
        void validInput_returnsOk() {
            // given
            var householdId = UUID.randomUUID();
            var expenseId = UUID.randomUUID();
            var update = new ExpenseUpdate().title("Updated Title");
            var updated = new Expense(expenseId, "Updated Title", 50.0,
                    UUID.randomUUID(), LocalDate.of(2026, 1, 15), UUID.randomUUID());
            doReturn(updated).when(expenseService).updateExpense(householdId, expenseId, update);

            // when
            var result = delegate.updateExpense(householdId, expenseId, Optional.of(update));

            // then
            assertThat(result.getStatusCode().value()).isEqualTo(200);
            assertThat(result.getBody()).isEqualTo(updated);
        }

        @Test
        void notFound_throwsExpenseNotFoundException() {
            // given
            var householdId = UUID.randomUUID();
            var expenseId = UUID.randomUUID();
            var update = new ExpenseUpdate();
            doThrow(ExpenseNotFoundException.class).when(expenseService).updateExpense(householdId, expenseId, update);

            // when / then
            assertThatThrownBy(() -> delegate.updateExpense(householdId, expenseId, Optional.of(update)))
                    .isInstanceOf(ExpenseNotFoundException.class);
        }
    }

    @Nested
    class deleteExpense {

        @Test
        void validInput_returnsNoContent() {
            // given
            var householdId = UUID.randomUUID();
            var expenseId = UUID.randomUUID();

            // when
            var result = delegate.deleteExpense(householdId, expenseId);

            // then
            assertThat(result.getStatusCode().value()).isEqualTo(204);
            verify(expenseService).deleteExpense(householdId, expenseId);
        }

        @Test
        void notFound_throwsExpenseNotFoundException() {
            // given
            var householdId = UUID.randomUUID();
            var expenseId = UUID.randomUUID();
            doThrow(ExpenseNotFoundException.class).when(expenseService).deleteExpense(householdId, expenseId);

            // when / then
            assertThatThrownBy(() -> delegate.deleteExpense(householdId, expenseId))
                    .isInstanceOf(ExpenseNotFoundException.class);
        }
    }

    @Nested
    class getDebtorExpenses {

        @Test
        void validInput_returnsOk() {
            // given
            var householdId = UUID.randomUUID();
            var payerId = UUID.randomUUID();
            var debtorId = UUID.randomUUID();
            var expense = new Expense(UUID.randomUUID(), "Groceries", 50.0,
                    UUID.randomUUID(), LocalDate.of(2026, 1, 15), UUID.randomUUID());
            doReturn(List.of(expense)).when(expenseService).getDebtorExpenses(householdId, payerId, debtorId);

            // when
            var result = delegate.getDebtorExpenses(householdId, payerId, debtorId);

            // then
            assertThat(result.getStatusCode().value()).isEqualTo(200);
            assertThat(result.getBody()).containsExactly(expense);
        }
    }
}
