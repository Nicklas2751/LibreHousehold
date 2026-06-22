package eu.wiegandt.librehousehold.expenses.controller;
import eu.wiegandt.librehousehold.expenses.exception.*;
import eu.wiegandt.librehousehold.expenses.mapper.*;
import eu.wiegandt.librehousehold.expenses.model.*;
import eu.wiegandt.librehousehold.expenses.repository.*;
import eu.wiegandt.librehousehold.expenses.service.*;

import eu.wiegandt.librehousehold.model.FinancialSummary;
import eu.wiegandt.librehousehold.model.MemberBalance;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

@ExtendWith(MockitoExtension.class)
class FinancialsApiDelegateImplTest {

    @Mock
    private FinancialService financialService;

    @InjectMocks
    private FinancialsApiDelegateImpl delegate;

    @Nested
    class getFinancialSummary {

        @Test
        void validInput_returnsOk() {
            // given
            var householdId = UUID.randomUUID();
            var userId = UUID.randomUUID();
            var summary = new FinancialSummary(0.0, 50.0);
            doReturn(summary).when(financialService).getFinancialSummary(householdId, userId);

            // when
            var result = delegate.getFinancialSummary(householdId, userId);

            // then
            assertThat(result.getStatusCode().value()).isEqualTo(200);
            assertThat(result.getBody()).isEqualTo(summary);
        }
    }

    @Nested
    class getMemberBalances {

        @Test
        void validInput_returnsOk() {
            // given
            var householdId = UUID.randomUUID();
            var userId = UUID.randomUUID();
            var balance = new MemberBalance(UUID.randomUUID(), 25.0);
            doReturn(List.of(balance)).when(financialService).getMemberBalances(householdId, userId);

            // when
            var result = delegate.getMemberBalances(householdId, userId);

            // then
            assertThat(result.getStatusCode().value()).isEqualTo(200);
            assertThat(result.getBody()).containsExactly(balance);
        }
    }
}
