package eu.wiegandt.librehousehold.expenses;

import eu.wiegandt.librehousehold.household.HouseholdQuery;
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

    @MockitoBean
    private HouseholdQuery householdQuery;

    @Nested
    class createExpense {

        @Test
        void validExpense_persistedInDatabase() {
            // given
            var householdId = UUID.randomUUID();
            var expense = Instancio.create(Expense.class);
            doReturn(true).when(householdQuery).householdExists(householdId);

            // when
            expenseService.createExpense(householdId, expense);

            // then
            var entity = expenseRepository.findById(expense.getId()).orElseThrow();
            assertThat(entity).usingRecursiveComparison().ignoringFields("isNew")
                    .isEqualTo(expenseMapper.toEntity(expense, householdId));
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
    }
}
