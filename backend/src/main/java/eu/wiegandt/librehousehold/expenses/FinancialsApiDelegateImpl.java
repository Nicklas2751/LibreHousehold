package eu.wiegandt.librehousehold.expenses;

import eu.wiegandt.librehousehold.api.FinancialsApiDelegate;
import eu.wiegandt.librehousehold.model.FinancialSummary;
import eu.wiegandt.librehousehold.model.MemberBalance;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class FinancialsApiDelegateImpl implements FinancialsApiDelegate {

    private final FinancialService financialService;

    FinancialsApiDelegateImpl(FinancialService financialService) {
        this.financialService = financialService;
    }

    @Override
    public ResponseEntity<FinancialSummary> getFinancialSummary(UUID householdId, UUID userId) {
        return ResponseEntity.ok(financialService.getFinancialSummary(householdId, userId));
    }

    @Override
    public ResponseEntity<List<MemberBalance>> getMemberBalances(UUID householdId, UUID userId) {
        return ResponseEntity.ok(financialService.getMemberBalances(householdId, userId));
    }
}
