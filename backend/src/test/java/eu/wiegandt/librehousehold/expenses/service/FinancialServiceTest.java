package eu.wiegandt.librehousehold.expenses.service;
import eu.wiegandt.librehousehold.expenses.exception.*;
import eu.wiegandt.librehousehold.expenses.mapper.*;
import eu.wiegandt.librehousehold.expenses.model.*;
import eu.wiegandt.librehousehold.expenses.repository.*;

import eu.wiegandt.librehousehold.household.MemberQuery;
import eu.wiegandt.librehousehold.model.FinancialSummary;
import eu.wiegandt.librehousehold.model.MemberBalance;
import org.instancio.Instancio;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.instancio.Select.field;
import static org.mockito.Mockito.doReturn;

@ExtendWith(MockitoExtension.class)
class FinancialServiceTest {

    @Mock
    private ExpenseRepository expenseRepository;

    @Mock
    private ReimbursementRepository reimbursementRepository;

    @Mock
    private MemberQuery memberQuery;

    @InjectMocks
    private FinancialService financialService;

    @Nested
    class getFinancialSummary {

        @Test
        void userPaidForOthers_returnsOwedToYouAmount() {
            // given
            var householdId = UUID.randomUUID();
            var userId = UUID.randomUUID();
            var otherMemberId = UUID.randomUUID();
            var expense = Instancio.of(ExpenseEntity.class)
                    .set(field(ExpenseEntity.class, "paidBy"), userId)
                    .set(field(ExpenseEntity.class, "amount"), BigDecimal.valueOf(100.0))
                    .set(field(ExpenseEntity.class, "splitBetween"), Set.of())
                    .create();
            doReturn(List.of(expense)).when(expenseRepository).findByHouseholdId(householdId);
            doReturn(List.of(userId, otherMemberId)).when(memberQuery).findMemberIdsByHouseholdId(householdId);
            doReturn(List.of()).when(reimbursementRepository).findByHouseholdId(householdId);
            var expected = new FinancialSummary(0.0, 50.0);

            // when
            var result = financialService.getFinancialSummary(householdId, userId);

            // then
            assertThat(result).usingRecursiveComparison().isEqualTo(expected);
        }

        @Test
        void othersPaidForUser_returnsYouOweAmount() {
            // given
            var householdId = UUID.randomUUID();
            var userId = UUID.randomUUID();
            var otherMemberId = UUID.randomUUID();
            var expense = Instancio.of(ExpenseEntity.class)
                    .set(field(ExpenseEntity.class, "paidBy"), otherMemberId)
                    .set(field(ExpenseEntity.class, "amount"), BigDecimal.valueOf(100.0))
                    .set(field(ExpenseEntity.class, "splitBetween"), Set.of())
                    .create();
            doReturn(List.of(expense)).when(expenseRepository).findByHouseholdId(householdId);
            doReturn(List.of(userId, otherMemberId)).when(memberQuery).findMemberIdsByHouseholdId(householdId);
            doReturn(List.of()).when(reimbursementRepository).findByHouseholdId(householdId);
            var expected = new FinancialSummary(50.0, 0.0);

            // when
            var result = financialService.getFinancialSummary(householdId, userId);

            // then
            assertThat(result).usingRecursiveComparison().isEqualTo(expected);
        }
    }

    @Nested
    class getMemberBalances {

        @Test
        void emptySplitBetween_splitsEquallyAmongAll() {
            // given
            var householdId = UUID.randomUUID();
            var userId = UUID.randomUUID();
            var memberB = UUID.randomUUID();
            var memberC = UUID.randomUUID();
            var expense = Instancio.of(ExpenseEntity.class)
                    .set(field(ExpenseEntity.class, "paidBy"), userId)
                    .set(field(ExpenseEntity.class, "amount"), BigDecimal.valueOf(90.0))
                    .set(field(ExpenseEntity.class, "splitBetween"), Set.of())
                    .create();
            doReturn(List.of(expense)).when(expenseRepository).findByHouseholdId(householdId);
            doReturn(List.of(userId, memberB, memberC)).when(memberQuery).findMemberIdsByHouseholdId(householdId);
            doReturn(List.of()).when(reimbursementRepository).findByHouseholdId(householdId);
            var expectedB = new MemberBalance(memberB, 30.0);
            var expectedC = new MemberBalance(memberC, 30.0);

            // when
            var result = financialService.getMemberBalances(householdId, userId);

            // then
            assertThat(result).usingRecursiveComparison()
                    .ignoringCollectionOrder()
                    .isEqualTo(List.of(expectedB, expectedC));
        }

        @Test
        void withConfirmedReimbursement_reducesBalance() {
            // given
            var householdId = UUID.randomUUID();
            var userId = UUID.randomUUID();
            var otherMemberId = UUID.randomUUID();
            var expense = Instancio.of(ExpenseEntity.class)
                    .set(field(ExpenseEntity.class, "paidBy"), userId)
                    .set(field(ExpenseEntity.class, "amount"), BigDecimal.valueOf(100.0))
                    .set(field(ExpenseEntity.class, "splitBetween"), Set.of())
                    .create();
            var reimbursement = Instancio.of(ReimbursementEntity.class)
                    .set(field(ReimbursementEntity.class, "creditorId"), userId)
                    .set(field(ReimbursementEntity.class, "debtorId"), otherMemberId)
                    .set(field(ReimbursementEntity.class, "amount"), BigDecimal.valueOf(25.0))
                    .set(field(ReimbursementEntity.class, "status"), "CONFIRMED")
                    .create();
            doReturn(List.of(expense)).when(expenseRepository).findByHouseholdId(householdId);
            doReturn(List.of(userId, otherMemberId)).when(memberQuery).findMemberIdsByHouseholdId(householdId);
            doReturn(List.of(reimbursement)).when(reimbursementRepository).findByHouseholdId(householdId);
            var expected = new MemberBalance(otherMemberId, 25.0);

            // when
            var result = financialService.getMemberBalances(householdId, userId);

            // then
            assertThat(result).singleElement().usingRecursiveComparison().isEqualTo(expected);
        }
    }

    @Nested
    class initBalances {

        @Test
        void memberIdsWithUser_returnsMapExcludingUser() {
            // given
            var userId = UUID.randomUUID();
            var memberA = UUID.randomUUID();
            var memberB = UUID.randomUUID();

            // when
            var result = financialService.initBalances(List.of(userId, memberA, memberB), userId);

            // then
            assertThat(result).isEqualTo(Map.of(memberA, 0.0, memberB, 0.0));
        }

        @Test
        void onlyUserId_returnsEmptyMap() {
            // given
            var userId = UUID.randomUUID();

            // when
            var result = financialService.initBalances(List.of(userId), userId);

            // then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    class resolvedSplitMembers {

        @Test
        void emptySplitBetween_returnsAllMembers() {
            // given
            var userId = UUID.randomUUID();
            var memberA = UUID.randomUUID();
            var expense = Instancio.of(ExpenseEntity.class)
                    .set(field(ExpenseEntity.class, "splitBetween"), Set.of())
                    .create();
            var allMembers = List.of(userId, memberA);

            // when
            var result = financialService.resolvedSplitMembers(expense, allMembers);

            // then
            assertThat(result).containsExactlyInAnyOrderElementsOf(allMembers);
        }

        @Test
        void explicitSplitBetween_returnsOnlyThoseMembers() {
            // given
            var paidBy = UUID.randomUUID();
            var memberA = UUID.randomUUID();
            var memberB = UUID.randomUUID();
            var expense = Instancio.of(ExpenseEntity.class)
                    .set(field(ExpenseEntity.class, "splitBetween"), Set.of(new ExpenseSplitRef(memberA)))
                    .create();
            var allMembers = List.of(paidBy, memberA, memberB);

            // when
            var result = financialService.resolvedSplitMembers(expense, allMembers);

            // then
            assertThat(result).containsExactly(memberA);
        }
    }

    @Nested
    class applyExpense {

        @Test
        void userPaid_addsShareForEachOtherSplitMember() {
            // given
            var userId = UUID.randomUUID();
            var memberA = UUID.randomUUID();
            var memberB = UUID.randomUUID();
            var expense = Instancio.of(ExpenseEntity.class)
                    .set(field(ExpenseEntity.class, "paidBy"), userId)
                    .set(field(ExpenseEntity.class, "amount"), BigDecimal.valueOf(90.0))
                    .set(field(ExpenseEntity.class, "splitBetween"), Set.of())
                    .create();
            var memberIds = List.of(userId, memberA, memberB);
            var balances = new HashMap<>(Map.of(memberA, 0.0, memberB, 0.0));

            // when
            financialService.applyExpense(expense, memberIds, userId, balances);

            // then
            assertThat(balances).isEqualTo(Map.of(memberA, 30.0, memberB, 30.0));
        }

        @Test
        void otherPaidAndUserInSplit_subtractsShareFromPayerBalance() {
            // given
            var userId = UUID.randomUUID();
            var payer = UUID.randomUUID();
            var expense = Instancio.of(ExpenseEntity.class)
                    .set(field(ExpenseEntity.class, "paidBy"), payer)
                    .set(field(ExpenseEntity.class, "amount"), BigDecimal.valueOf(100.0))
                    .set(field(ExpenseEntity.class, "splitBetween"), Set.of())
                    .create();
            var memberIds = List.of(userId, payer);
            var balances = new HashMap<>(Map.of(payer, 0.0));

            // when
            financialService.applyExpense(expense, memberIds, userId, balances);

            // then
            assertThat(balances).isEqualTo(Map.of(payer, -50.0));
        }

        @Test
        void userNotInSplit_doesNotChangeBalances() {
            // given
            var userId = UUID.randomUUID();
            var payer = UUID.randomUUID();
            var otherMember = UUID.randomUUID();
            var expense = Instancio.of(ExpenseEntity.class)
                    .set(field(ExpenseEntity.class, "paidBy"), payer)
                    .set(field(ExpenseEntity.class, "amount"), BigDecimal.valueOf(100.0))
                    .set(field(ExpenseEntity.class, "splitBetween"), Set.of(new ExpenseSplitRef(otherMember)))
                    .create();
            var memberIds = List.of(userId, payer, otherMember);
            var balances = new HashMap<>(Map.of(payer, 0.0));

            // when
            financialService.applyExpense(expense, memberIds, userId, balances);

            // then
            assertThat(balances).isEqualTo(Map.of(payer, 0.0));
        }
    }

    @Nested
    class applyConfirmedReimbursements {

        @Test
        void confirmedReimbursement_userIsCreditor_reducesDebtorBalance() {
            // given
            var userId = UUID.randomUUID();
            var debtor = UUID.randomUUID();
            var reimbursement = Instancio.of(ReimbursementEntity.class)
                    .set(field(ReimbursementEntity.class, "creditorId"), userId)
                    .set(field(ReimbursementEntity.class, "debtorId"), debtor)
                    .set(field(ReimbursementEntity.class, "amount"), BigDecimal.valueOf(30.0))
                    .set(field(ReimbursementEntity.class, "status"), "CONFIRMED")
                    .create();
            var balances = new HashMap<>(Map.of(debtor, 50.0));

            // when
            financialService.applyConfirmedReimbursements(List.of(reimbursement), userId, balances);

            // then
            assertThat(balances).isEqualTo(Map.of(debtor, 20.0));
        }

        @Test
        void confirmedReimbursement_userIsDebtor_increasesCreditorBalance() {
            // given
            var userId = UUID.randomUUID();
            var creditor = UUID.randomUUID();
            var reimbursement = Instancio.of(ReimbursementEntity.class)
                    .set(field(ReimbursementEntity.class, "creditorId"), creditor)
                    .set(field(ReimbursementEntity.class, "debtorId"), userId)
                    .set(field(ReimbursementEntity.class, "amount"), BigDecimal.valueOf(30.0))
                    .set(field(ReimbursementEntity.class, "status"), "CONFIRMED")
                    .create();
            var balances = new HashMap<>(Map.of(creditor, -50.0));

            // when
            financialService.applyConfirmedReimbursements(List.of(reimbursement), userId, balances);

            // then
            assertThat(balances).isEqualTo(Map.of(creditor, -20.0));
        }

        @Test
        void pendingReimbursement_doesNotChangeBalances() {
            // given
            var userId = UUID.randomUUID();
            var debtor = UUID.randomUUID();
            var reimbursement = Instancio.of(ReimbursementEntity.class)
                    .set(field(ReimbursementEntity.class, "creditorId"), userId)
                    .set(field(ReimbursementEntity.class, "debtorId"), debtor)
                    .set(field(ReimbursementEntity.class, "amount"), BigDecimal.valueOf(30.0))
                    .set(field(ReimbursementEntity.class, "status"), "PENDING")
                    .create();
            var balances = new HashMap<>(Map.of(debtor, 50.0));

            // when
            financialService.applyConfirmedReimbursements(List.of(reimbursement), userId, balances);

            // then
            assertThat(balances).isEqualTo(Map.of(debtor, 50.0));
        }

        @Test
        void rejectedReimbursement_doesNotChangeBalances() {
            // given
            var userId = UUID.randomUUID();
            var debtor = UUID.randomUUID();
            var reimbursement = Instancio.of(ReimbursementEntity.class)
                    .set(field(ReimbursementEntity.class, "creditorId"), userId)
                    .set(field(ReimbursementEntity.class, "debtorId"), debtor)
                    .set(field(ReimbursementEntity.class, "amount"), BigDecimal.valueOf(30.0))
                    .set(field(ReimbursementEntity.class, "status"), "REJECTED")
                    .create();
            var balances = new HashMap<>(Map.of(debtor, 50.0));

            // when
            financialService.applyConfirmedReimbursements(List.of(reimbursement), userId, balances);

            // then
            assertThat(balances).isEqualTo(Map.of(debtor, 50.0));
        }
    }
}
