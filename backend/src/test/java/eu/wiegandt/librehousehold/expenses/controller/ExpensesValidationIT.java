package eu.wiegandt.librehousehold.expenses.controller;
import eu.wiegandt.librehousehold.expenses.exception.*;
import eu.wiegandt.librehousehold.expenses.mapper.*;
import eu.wiegandt.librehousehold.expenses.model.*;
import eu.wiegandt.librehousehold.expenses.repository.*;
import eu.wiegandt.librehousehold.expenses.service.*;

import eu.wiegandt.librehousehold.api.ExpensesApiController;
import eu.wiegandt.librehousehold.api.ReimbursementsApiController;
import eu.wiegandt.librehousehold.model.Category;
import eu.wiegandt.librehousehold.model.Expense;
import eu.wiegandt.librehousehold.model.ReimbursementCreate;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {ExpensesApiController.class, ReimbursementsApiController.class})
@Import(ExpensesApiDelegateImpl.class)
@ImportAutoConfiguration(JacksonAutoConfiguration.class)
class ExpensesValidationIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private CategoryService categoryService;

    @MockitoBean
    private ExpenseService expenseService;

    @Nested
    class createExpense {

        @Test
        void titleTooShort_returns400() throws Exception {
            // given
            var expense = new Expense(UUID.randomUUID(), "ab", 50.0,
                    UUID.randomUUID(), LocalDate.of(2026, 1, 15), UUID.randomUUID());

            // when / then
            mockMvc.perform(post("/v1/household/{householdId}/expenses", UUID.randomUUID())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(expense)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void titleTooLong_returns400() throws Exception {
            // given
            var expense = new Expense(UUID.randomUUID(), "x".repeat(201), 50.0,
                    UUID.randomUUID(), LocalDate.of(2026, 1, 15), UUID.randomUUID());

            // when / then
            mockMvc.perform(post("/v1/household/{householdId}/expenses", UUID.randomUUID())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(expense)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void amountTooLarge_returns400() throws Exception {
            // given
            var expense = new Expense(UUID.randomUUID(), "Groceries", 100000000.0,
                    UUID.randomUUID(), LocalDate.of(2026, 1, 15), UUID.randomUUID());

            // when / then
            mockMvc.perform(post("/v1/household/{householdId}/expenses", UUID.randomUUID())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(expense)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void notesTooLong_returns400() throws Exception {
            // given
            var expense = new Expense(UUID.randomUUID(), "Groceries", 50.0,
                    UUID.randomUUID(), LocalDate.of(2026, 1, 15), UUID.randomUUID())
                    .notes("x".repeat(1001));

            // when / then
            mockMvc.perform(post("/v1/household/{householdId}/expenses", UUID.randomUUID())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(expense)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    class createCategory {

        @Test
        void nameTooLong_returns400() throws Exception {
            // given
            var category = new Category(UUID.randomUUID(), "x".repeat(51));

            // when / then
            mockMvc.perform(post("/v1/household/{householdId}/categories", UUID.randomUUID())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(category)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    class createReimbursement {

        @Test
        void amountTooLarge_returns400() throws Exception {
            // given
            var reimbursement = new ReimbursementCreate(100000000.0, UUID.randomUUID(), UUID.randomUUID());

            // when / then
            mockMvc.perform(post("/v1/household/{householdId}/reimbursements", UUID.randomUUID())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(reimbursement)))
                    .andExpect(status().isBadRequest());
        }
    }
}
