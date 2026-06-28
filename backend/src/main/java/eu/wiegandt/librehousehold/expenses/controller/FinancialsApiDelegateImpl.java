package eu.wiegandt.librehousehold.expenses.controller;

import eu.wiegandt.librehousehold.api.FinancialsApiDelegate;
import eu.wiegandt.librehousehold.auth.InHousehold;
import eu.wiegandt.librehousehold.expenses.service.FinancialService;
import eu.wiegandt.librehousehold.model.FinancialSummary;
import eu.wiegandt.librehousehold.model.MemberBalance;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class FinancialsApiDelegateImpl implements FinancialsApiDelegate {

    private final FinancialService financialService;

    public FinancialsApiDelegateImpl(FinancialService financialService) {
        this.financialService = financialService;
    }

    @Override
    @InHousehold
    public ResponseEntity<FinancialSummary> getFinancialSummary(UUID householdId, UUID userId) {
        return ResponseEntity.ok(financialService.getFinancialSummary(householdId, userId));
    }

    @Override
    @InHousehold
    public ResponseEntity<List<MemberBalance>> getMemberBalances(UUID householdId, UUID userId) {
        return ResponseEntity.ok(financialService.getMemberBalances(householdId, userId));
    }
}
