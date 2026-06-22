package eu.wiegandt.librehousehold.expenses.service;
import eu.wiegandt.librehousehold.expenses.exception.*;
import eu.wiegandt.librehousehold.expenses.mapper.*;
import eu.wiegandt.librehousehold.expenses.model.*;
import eu.wiegandt.librehousehold.expenses.repository.*;

import eu.wiegandt.librehousehold.model.Expense;
import eu.wiegandt.librehousehold.model.ReimbursementCreate;
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
@Import({ExpenseMapperImpl.class, ExpenseService.class, ReimbursementService.class, ReimbursementMapperImpl.class})
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@TestPropertySource(properties = {
        "spring.flyway.locations=classpath:db/migration"
})
class ReimbursementServiceIT {

    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:latest"));

    @Autowired
    private ReimbursementService reimbursementService;

    @Autowired
    private ReimbursementRepository reimbursementRepository;

    @Autowired
    private ExpenseService expenseService;

    @MockitoBean
    private eu.wiegandt.librehousehold.household.HouseholdQuery householdQuery;

    @MockitoBean
    private eu.wiegandt.librehousehold.household.MemberQuery memberQuery;

    @Nested
    class createReimbursement {

        @Test
        void populatesSettlementExpenseTable() {
            // given
            var householdId = UUID.randomUUID();
            var creditorId = UUID.randomUUID();
            var debtorId = UUID.randomUUID();
            doReturn(true).when(householdQuery).householdExists(householdId);

            var expense = expenseService.createExpense(householdId, Instancio.of(Expense.class)
                    .set(field(Expense.class, "paidBy"), creditorId)
                    .set(field(Expense.class, "splitBetween"), List.of(debtorId))
                    .create());

            var create = new ReimbursementCreate()
                    .creditorId(creditorId)
                    .debtorId(debtorId)
                    .amount(10.0);

            // when
            var result = reimbursementService.createReimbursement(householdId, create);

            // then
            var saved = reimbursementRepository.findById(result.getId()).orElseThrow();
            assertThat(saved.getCoveredExpenses())
                    .containsExactly(new SettlementExpenseRef(expense.getId()));
        }

        @Test
        void noDebtorExpenses_createSettlementWithEmptyCoverage() {
            // given
            var householdId = UUID.randomUUID();
            var create = new ReimbursementCreate()
                    .creditorId(UUID.randomUUID())
                    .debtorId(UUID.randomUUID())
                    .amount(5.0);

            // when
            var result = reimbursementService.createReimbursement(householdId, create);

            // then
            var saved = reimbursementRepository.findById(result.getId()).orElseThrow();
            assertThat(saved.getCoveredExpenses()).isEmpty();
        }
    }
}
