package eu.wiegandt.librehousehold.expenses;

import eu.wiegandt.librehousehold.household.HouseholdQuery;
import eu.wiegandt.librehousehold.household.MemberQuery;
import eu.wiegandt.librehousehold.model.Expense;
import org.instancio.Instancio;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jdbc.test.autoconfigure.DataJdbcTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.instancio.Select.field;
import static org.mockito.Mockito.doReturn;

@DataJdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ExpenseMapperImpl.class, ExpenseService.class})
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@TestPropertySource(properties = {
        "spring.flyway.locations=classpath:db/migration"
})
class ExpenseServiceIT {

    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:latest"));

    @Autowired
    private ExpenseService expenseService;

    @Autowired
    private ExpenseRepository expenseRepository;

    @Autowired
    private ExpenseMapper expenseMapper;

    @Autowired
    private ReimbursementRepository reimbursementRepository;

    @MockitoBean
    private HouseholdQuery householdQuery;

    @MockitoBean
    private MemberQuery memberQuery;

    @Nested
    class createExpense {

        @Test
        void validExpense_persistedInDatabase() {
            // given
            var householdId = UUID.randomUUID();
            var expense = Instancio.of(Expense.class)
                    .set(field(Expense.class, "splitBetween"), List.of(UUID.randomUUID()))
                    .create();
            doReturn(true).when(householdQuery).householdExists(householdId);

            // when
            expenseService.createExpense(householdId, expense);

            // then
            var entity = expenseRepository.findById(expense.getId()).orElseThrow();
            assertThat(entity).usingRecursiveComparison().ignoringFields("isNew")
                    .isEqualTo(expenseMapper.toEntity(expense, householdId));
        }

        @Test
        void emptySplit_persistsAllMembersExplicitly() {
            // given
            var householdId = UUID.randomUUID();
            var member1 = UUID.randomUUID();
            var member2 = UUID.randomUUID();
            var expense = Instancio.of(Expense.class)
                    .set(field(Expense.class, "splitBetween"), List.of())
                    .create();
            doReturn(true).when(householdQuery).householdExists(householdId);
            doReturn(List.of(member1, member2)).when(memberQuery).findMemberIdsByHouseholdId(householdId);

            // when
            expenseService.createExpense(householdId, expense);

            // then
            var entity = expenseRepository.findById(expense.getId()).orElseThrow();
            assertThat(entity.getSplitBetween())
                    .containsExactlyInAnyOrder(new ExpenseSplitRef(member1), new ExpenseSplitRef(member2));
        }
    }

    @Nested
    class getDebtorExpenses {

        @Test
        void mixedExpenses_returnsOnlyMatchingEntries() {
            // given
            var householdId = UUID.randomUUID();
            var otherHouseholdId = UUID.randomUUID();
            var payerId = UUID.randomUUID();
            var debtorId = UUID.randomUUID();
            var otherMemberId = UUID.randomUUID();
            doReturn(true).when(householdQuery).householdExists(householdId);
            doReturn(true).when(householdQuery).householdExists(otherHouseholdId);

            // matching: payer paid, debtor explicitly listed in split alongside another member
            var matching1 = expenseService.createExpense(householdId, Instancio.of(Expense.class)
                    .set(field(Expense.class, "paidBy"), payerId)
                    .set(field(Expense.class, "splitBetween"), List.of(debtorId, otherMemberId))
                    .create());

            // matching: payer paid, no explicit split — everyone owes
            var matching2 = expenseService.createExpense(householdId, Instancio.of(Expense.class)
                    .set(field(Expense.class, "paidBy"), payerId)
                    .set(field(Expense.class, "splitBetween"), List.of())
                    .create());

            // not matching: roles reversed — debtor paid, payer is in the split
            expenseService.createExpense(householdId, Instancio.of(Expense.class)
                    .set(field(Expense.class, "paidBy"), debtorId)
                    .set(field(Expense.class, "splitBetween"), List.of(payerId))
                    .create());

            // not matching: payer paid but debtor is not in the explicit split
            expenseService.createExpense(householdId, Instancio.of(Expense.class)
                    .set(field(Expense.class, "paidBy"), payerId)
                    .set(field(Expense.class, "splitBetween"), List.of(otherMemberId))
                    .create());

            // not matching: third party paid, debtor is in the split
            expenseService.createExpense(householdId, Instancio.of(Expense.class)
                    .set(field(Expense.class, "paidBy"), otherMemberId)
                    .set(field(Expense.class, "splitBetween"), List.of(debtorId))
                    .create());

            // not matching: correct payer and debtor but different household
            expenseService.createExpense(otherHouseholdId, Instancio.of(Expense.class)
                    .set(field(Expense.class, "paidBy"), payerId)
                    .set(field(Expense.class, "splitBetween"), List.of(debtorId))
                    .create());

            // when
            var result = expenseService.getDebtorExpenses(householdId, payerId, debtorId);

            // then
            assertThat(result).extracting(Expense::getId)
                    .containsExactlyInAnyOrder(matching1.getId(), matching2.getId());
        }

        @Test
        void reverseDirectionConfirmedSettlement_isAlsoCutoff() {
            // given
            var householdId = UUID.randomUUID();
            var payerId = UUID.randomUUID();
            var debtorId = UUID.randomUUID();
            doReturn(true).when(householdQuery).householdExists(householdId);

            // payerId paid this expense before the settlement; split is empty (all members owe)
            expenseService.createExpense(householdId, Instancio.of(Expense.class)
                    .set(field(Expense.class, "paidBy"), payerId)
                    .set(field(Expense.class, "splitBetween"), List.of())
                    .set(field(Expense.class, "date"), LocalDate.of(2024, 1, 1))
                    .create());

            // settlement in REVERSE direction: creditor=debtorId, debtor=payerId
            var reverseSettlement = new ReimbursementEntity(UUID.randomUUID(), householdId,
                    BigDecimal.ONE, debtorId, payerId, "CONFIRMED", null,
                    LocalDateTime.of(2024, 6, 1, 0, 0));
            reimbursementRepository.save(reverseSettlement);

            // when: query for payerId's expenses where debtorId owes
            var result = expenseService.getDebtorExpenses(householdId, payerId, debtorId);

            // then: expense predates the settlement → excluded
            assertThat(result).isEmpty();
        }

        @Test
        void expensePredatesConfirmedSettlement_isExcluded() {
            // given
            var householdId = UUID.randomUUID();
            var payerId = UUID.randomUUID();
            var debtorId = UUID.randomUUID();
            doReturn(true).when(householdQuery).householdExists(householdId);

            expenseService.createExpense(householdId, Instancio.of(Expense.class)
                    .set(field(Expense.class, "paidBy"), payerId)
                    .set(field(Expense.class, "splitBetween"), List.of())
                    .set(field(Expense.class, "date"), LocalDate.of(2024, 1, 1))
                    .create());

            var confirmedSettlement = new ReimbursementEntity(UUID.randomUUID(), householdId,
                    BigDecimal.ONE, payerId, debtorId, "CONFIRMED", null,
                    LocalDateTime.of(2024, 6, 1, 0, 0));
            reimbursementRepository.save(confirmedSettlement);

            // when
            var result = expenseService.getDebtorExpenses(householdId, payerId, debtorId);

            // then
            assertThat(result).isEmpty();
        }

        @Test
        void expensePostdatesConfirmedSettlement_isIncluded() {
            // given
            var householdId = UUID.randomUUID();
            var payerId = UUID.randomUUID();
            var debtorId = UUID.randomUUID();
            doReturn(true).when(householdQuery).householdExists(householdId);

            var expense = expenseService.createExpense(householdId, Instancio.of(Expense.class)
                    .set(field(Expense.class, "paidBy"), payerId)
                    .set(field(Expense.class, "splitBetween"), List.of())
                    .set(field(Expense.class, "date"), LocalDate.of(2024, 7, 1))
                    .create());

            var confirmedSettlement = new ReimbursementEntity(UUID.randomUUID(), householdId,
                    BigDecimal.ONE, payerId, debtorId, "CONFIRMED", null,
                    LocalDateTime.of(2024, 6, 1, 0, 0));
            reimbursementRepository.save(confirmedSettlement);

            // when
            var result = expenseService.getDebtorExpenses(householdId, payerId, debtorId);

            // then
            assertThat(result).extracting(Expense::getId).containsExactly(expense.getId());
        }

        @Test
        void pendingSettlementIsNotCutoff_expenseIsIncluded() {
            // given
            var householdId = UUID.randomUUID();
            var payerId = UUID.randomUUID();
            var debtorId = UUID.randomUUID();
            doReturn(true).when(householdQuery).householdExists(householdId);

            var expense = expenseService.createExpense(householdId, Instancio.of(Expense.class)
                    .set(field(Expense.class, "paidBy"), payerId)
                    .set(field(Expense.class, "splitBetween"), List.of())
                    .set(field(Expense.class, "date"), LocalDate.of(2024, 1, 1))
                    .create());

            var pendingSettlement = new ReimbursementEntity(UUID.randomUUID(), householdId,
                    BigDecimal.ONE, payerId, debtorId, "PENDING", null,
                    LocalDateTime.of(2024, 6, 1, 0, 0));
            reimbursementRepository.save(pendingSettlement);

            // when
            var result = expenseService.getDebtorExpenses(householdId, payerId, debtorId);

            // then
            assertThat(result).extracting(Expense::getId).containsExactly(expense.getId());
        }
    }

    @Nested
    class getExpenses {

        @Test
        void debtorInActiveSettlementCoveringExpense_isMutableFalse() {
            // given
            var householdId = UUID.randomUUID();
            var creditorId = UUID.randomUUID();
            var debtorId = UUID.randomUUID();
            doReturn(true).when(householdQuery).householdExists(householdId);

            // debtorId paid this expense; creditorId is explicitly in the split
            expenseService.createExpense(householdId, Instancio.of(Expense.class)
                    .set(field(Expense.class, "paidBy"), debtorId)
                    .set(field(Expense.class, "splitBetween"), List.of(creditorId, debtorId))
                    .create());

            // settlement: creditor=creditorId, debtor=debtorId → active (CONFIRMED)
            var settlement = new ReimbursementEntity(UUID.randomUUID(), householdId,
                    BigDecimal.ONE, creditorId, debtorId, "CONFIRMED", null, LocalDateTime.now());
            reimbursementRepository.save(settlement);

            // when
            var result = expenseService.getExpenses(householdId);

            // then: expense is covered by the settlement → not mutable
            assertThat(result).singleElement()
                    .satisfies(expense -> assertThat(expense.getIsMutable()).contains(false));
        }

        @Test
        void debtorInSettlementButCreditorNotInSplit_isMutableTrue() {
            // given
            var householdId = UUID.randomUUID();
            var creditorId = UUID.randomUUID();
            var debtorId = UUID.randomUUID();
            var unrelatedMember = UUID.randomUUID();
            doReturn(true).when(householdQuery).householdExists(householdId);

            // debtorId paid this expense; split only contains debtorId and unrelatedMember
            expenseService.createExpense(householdId, Instancio.of(Expense.class)
                    .set(field(Expense.class, "paidBy"), debtorId)
                    .set(field(Expense.class, "splitBetween"), List.of(debtorId, unrelatedMember))
                    .create());

            // settlement: creditor=creditorId, debtor=debtorId → but creditorId is NOT in the expense split
            var settlement = new ReimbursementEntity(UUID.randomUUID(), householdId,
                    BigDecimal.ONE, creditorId, debtorId, "CONFIRMED", null, LocalDateTime.now());
            reimbursementRepository.save(settlement);

            // when
            var result = expenseService.getExpenses(householdId);

            // then: settlement does not cover this expense → still mutable
            assertThat(result).singleElement()
                    .satisfies(expense -> assertThat(expense.getIsMutable()).contains(true));
        }
    }
}
