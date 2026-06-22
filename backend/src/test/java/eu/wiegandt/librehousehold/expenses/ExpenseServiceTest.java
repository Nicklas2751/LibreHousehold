package eu.wiegandt.librehousehold.expenses;

import eu.wiegandt.librehousehold.household.HouseholdDeleted;
import eu.wiegandt.librehousehold.household.HouseholdQuery;
import eu.wiegandt.librehousehold.household.MemberQuery;
import eu.wiegandt.librehousehold.model.Expense;
import eu.wiegandt.librehousehold.model.ExpenseUpdate;
import org.instancio.Instancio;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.instancio.Select.field;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ExpenseServiceTest {

    @Mock
    private ExpenseRepository expenseRepository;

    @Mock
    private ReimbursementRepository reimbursementRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Spy
    private ExpenseMapper expenseMapper = Mappers.getMapper(ExpenseMapper.class);

    @Mock
    private HouseholdQuery householdQuery;

    @Mock
    private MemberQuery memberQuery;

    @InjectMocks
    private ExpenseService expenseService;

    @Nested
    class getExpenses {

        @Test
        void noActiveReimbursement_returnsExpenseWithIsMutableTrue() {
            // given
            var householdId = UUID.randomUUID();
            var entity = Instancio.create(ExpenseEntity.class);
            doReturn(List.of(entity)).when(expenseRepository).findByHouseholdIdOrderByDateDesc(householdId);
            var expected = expenseMapper.toExpense(entity, true);

            // when
            var result = expenseService.getExpenses(householdId);

            // then
            assertThat(result).singleElement().usingRecursiveComparison().isEqualTo(expected);
        }

        @Test
        void activeSettlementCoveringExpense_returnsIsMutableFalse() {
            // given
            var householdId = UUID.randomUUID();
            var entity = Instancio.create(ExpenseEntity.class);
            doReturn(List.of(entity)).when(expenseRepository).findByHouseholdIdOrderByDateDesc(householdId);
            doReturn(true).when(reimbursementRepository)
                    .existsActiveSettlementCoveringExpense(any(), any());
            var expected = expenseMapper.toExpense(entity, false);

            // when
            var result = expenseService.getExpenses(householdId);

            // then
            assertThat(result).singleElement().usingRecursiveComparison().isEqualTo(expected);
        }
    }

    @Nested
    class createExpense {

        @Test
        void unknownHousehold_throwsHouseholdNotFoundException() {
            // given
            var householdId = UUID.randomUUID();
            var expense = Instancio.create(Expense.class);
            doReturn(false).when(householdQuery).householdExists(householdId);

            // when / then
            assertThatThrownBy(() -> expenseService.createExpense(householdId, expense))
                    .isInstanceOf(HouseholdNotFoundException.class);
        }

        @Test
        void emptySplit_resolvesToAllCurrentMembers() {
            // given
            var householdId = UUID.randomUUID();
            var member1 = UUID.randomUUID();
            var member2 = UUID.randomUUID();
            var expense = Instancio.of(Expense.class)
                    .set(field(Expense.class, "splitBetween"), List.of())
                    .create();
            var savedEntity = Instancio.create(ExpenseEntity.class);
            doReturn(true).when(householdQuery).householdExists(householdId);
            doReturn(List.of(member1, member2)).when(memberQuery).findMemberIdsByHouseholdId(householdId);
            doReturn(savedEntity).when(expenseRepository).save(any(ExpenseEntity.class));

            // when
            expenseService.createExpense(householdId, expense);

            // then
            var captor = ArgumentCaptor.forClass(ExpenseEntity.class);
            verify(expenseRepository).save(captor.capture());
            assertThat(captor.getValue().getSplitBetween())
                    .containsExactlyInAnyOrder(new ExpenseSplitRef(member1), new ExpenseSplitRef(member2));
        }

        @Test
        void validInput_returnsCreatedExpense() {
            // given
            var householdId = UUID.randomUUID();
            var expense = Instancio.create(Expense.class);
            var savedEntity = Instancio.create(ExpenseEntity.class);
            doReturn(true).when(householdQuery).householdExists(householdId);
            doReturn(savedEntity).when(expenseRepository).save(any(ExpenseEntity.class));
            var expected = expenseMapper.toExpense(savedEntity, true);

            // when
            var result = expenseService.createExpense(householdId, expense);

            // then
            assertThat(result).usingRecursiveComparison().isEqualTo(expected);
        }
    }

    @Nested
    class updateExpense {

        @Test
        void notFound_throwsExpenseNotFoundException() {
            // given
            var householdId = UUID.randomUUID();
            var expenseId = UUID.randomUUID();
            doReturn(Optional.empty()).when(expenseRepository).findByIdAndHouseholdId(expenseId, householdId);

            // when / then
            assertThatThrownBy(() -> expenseService.updateExpense(householdId, expenseId, new ExpenseUpdate()))
                    .isInstanceOf(ExpenseNotFoundException.class);
        }

        @Test
        void notMutable_throwsExpenseNotMutableException() {
            // given
            var householdId = UUID.randomUUID();
            var expenseId = UUID.randomUUID();
            var entity = Instancio.create(ExpenseEntity.class);
            doReturn(Optional.of(entity)).when(expenseRepository).findByIdAndHouseholdId(expenseId, householdId);
            doReturn(true).when(reimbursementRepository)
                    .existsActiveSettlementCoveringExpense(any(), any());

            // when / then
            assertThatThrownBy(() -> expenseService.updateExpense(householdId, expenseId, new ExpenseUpdate()))
                    .isInstanceOf(ExpenseNotMutableException.class);
        }

        @Test
        void existingExpense_returnsUpdated() {
            // given
            var householdId = UUID.randomUUID();
            var expenseId = UUID.randomUUID();
            var entity = Instancio.create(ExpenseEntity.class);
            doReturn(Optional.of(entity)).when(expenseRepository).findByIdAndHouseholdId(expenseId, householdId);
            var update = new ExpenseUpdate().title("Updated Title");

            // when
            var result = expenseService.updateExpense(householdId, expenseId, update);

            // then — entity is mutated in-place by the mapper during the service call
            var expected = expenseMapper.toExpense(entity, true);
            assertThat(result).usingRecursiveComparison().isEqualTo(expected);
        }
    }

    @Nested
    class deleteExpense {

        @Test
        void notFound_throwsExpenseNotFoundException() {
            // given
            var householdId = UUID.randomUUID();
            var expenseId = UUID.randomUUID();
            doReturn(Optional.empty()).when(expenseRepository).findByIdAndHouseholdId(expenseId, householdId);

            // when / then
            assertThatThrownBy(() -> expenseService.deleteExpense(householdId, expenseId))
                    .isInstanceOf(ExpenseNotFoundException.class);
        }

        @Test
        void notMutable_throwsExpenseNotMutableException() {
            // given
            var householdId = UUID.randomUUID();
            var expenseId = UUID.randomUUID();
            var entity = Instancio.create(ExpenseEntity.class);
            doReturn(Optional.of(entity)).when(expenseRepository).findByIdAndHouseholdId(expenseId, householdId);
            doReturn(true).when(reimbursementRepository)
                    .existsActiveSettlementCoveringExpense(any(), any());

            // when / then
            assertThatThrownBy(() -> expenseService.deleteExpense(householdId, expenseId))
                    .isInstanceOf(ExpenseNotMutableException.class);
        }

        @Test
        void existingMutableExpense_deletesSuccessfully() {
            // given
            var householdId = UUID.randomUUID();
            var expenseId = UUID.randomUUID();
            var entity = Instancio.create(ExpenseEntity.class);
            doReturn(Optional.of(entity)).when(expenseRepository).findByIdAndHouseholdId(expenseId, householdId);

            // when
            expenseService.deleteExpense(householdId, expenseId);

            // then
            verify(expenseRepository).deleteById(expenseId);
        }
    }

    @Nested
    class getExpense {

        @Test
        void notFound_throwsExpenseNotFoundException() {
            // given
            var householdId = UUID.randomUUID();
            var expenseId = UUID.randomUUID();
            doReturn(Optional.empty()).when(expenseRepository).findByIdAndHouseholdId(expenseId, householdId);

            // when / then
            assertThatThrownBy(() -> expenseService.getExpense(householdId, expenseId))
                    .isInstanceOf(ExpenseNotFoundException.class);
        }

        @Test
        void existingExpense_returnsMappedExpense() {
            // given
            var householdId = UUID.randomUUID();
            var expenseId = UUID.randomUUID();
            var entity = Instancio.create(ExpenseEntity.class);
            doReturn(Optional.of(entity)).when(expenseRepository).findByIdAndHouseholdId(expenseId, householdId);
            var expected = expenseMapper.toExpense(entity, true);

            // when
            var result = expenseService.getExpense(householdId, expenseId);

            // then
            assertThat(result).usingRecursiveComparison().isEqualTo(expected);
        }
    }

    @Nested
    class getDebtorExpenses {

        @Test
        void validInput_returnsMappedExpenses() {
            // given
            var householdId = UUID.randomUUID();
            var payerId = UUID.randomUUID();
            var debtorId = UUID.randomUUID();
            var entity = Instancio.create(ExpenseEntity.class);
            doReturn(List.of(entity)).when(expenseRepository).findDebtorExpenses(householdId, payerId, debtorId);
            var expected = expenseMapper.toExpense(entity, true);

            // when
            var result = expenseService.getDebtorExpenses(householdId, payerId, debtorId);

            // then
            assertThat(result).singleElement().usingRecursiveComparison().isEqualTo(expected);
        }
    }

    @Nested
    class onHouseholdDeleted {

        @Test
        void deletesAllExpenseData() {
            // given
            var householdId = UUID.randomUUID();
            var event = new HouseholdDeleted(householdId);
            var entities = Instancio.ofList(ExpenseEntity.class).create();
            doReturn(entities).when(expenseRepository).findByHouseholdId(householdId);

            // when
            expenseService.onHouseholdDeleted(event);

            // then
            verify(reimbursementRepository).deleteByHouseholdId(householdId);
            verify(expenseRepository).findByHouseholdId(householdId);
            verify(expenseRepository).deleteAll(entities);
            verify(categoryRepository).deleteByHouseholdId(householdId);
        }
    }
}
