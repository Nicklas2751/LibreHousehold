package eu.wiegandt.librehousehold.expenses.service;

import eu.wiegandt.librehousehold.expenses.model.ExpenseEntity;
import eu.wiegandt.librehousehold.expenses.model.ExpenseSplitRef;
import eu.wiegandt.librehousehold.expenses.model.ReimbursementEntity;
import eu.wiegandt.librehousehold.expenses.repository.ExpenseRepository;
import eu.wiegandt.librehousehold.expenses.repository.ReimbursementRepository;
import eu.wiegandt.librehousehold.household.MemberQuery;
import eu.wiegandt.librehousehold.model.FinancialSummary;
import eu.wiegandt.librehousehold.model.MemberBalance;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class FinancialService {

    private static final String STATUS_CONFIRMED = "CONFIRMED";

    private final ExpenseRepository expenseRepository;
    private final ReimbursementRepository reimbursementRepository;
    private final MemberQuery memberQuery;

    public FinancialService(ExpenseRepository expenseRepository,
                     ReimbursementRepository reimbursementRepository,
                     MemberQuery memberQuery) {
        this.expenseRepository = expenseRepository;
        this.reimbursementRepository = reimbursementRepository;
        this.memberQuery = memberQuery;
    }

    public FinancialSummary getFinancialSummary(UUID householdId, UUID userId) {
        var balances = computeBalances(householdId, userId);
        double owedToYou = balances.values().stream()
                .filter(balance -> balance > 0)
                .mapToDouble(Double::doubleValue)
                .sum();
        double youOwe = balances.values().stream()
                .filter(balance -> balance < 0)
                .mapToDouble(balance -> -balance)
                .sum();
        return new FinancialSummary(youOwe, owedToYou);
    }

    public List<MemberBalance> getMemberBalances(UUID householdId, UUID userId) {
        return computeBalances(householdId, userId).entrySet().stream()
                .map(entry -> new MemberBalance(entry.getKey(), entry.getValue()))
                .toList();
    }

    private Map<UUID, Double> computeBalances(UUID householdId, UUID userId) {
        var expenses = expenseRepository.findByHouseholdId(householdId);
        var memberIds = memberQuery.findMemberIdsByHouseholdId(householdId);
        var reimbursements = reimbursementRepository.findByHouseholdId(householdId);

        var balances = initBalances(memberIds, userId);
        expenses.forEach(expense -> applyExpense(expense, memberIds, userId, balances));
        applyConfirmedReimbursements(reimbursements, userId, balances);
        return balances;
    }

    Map<UUID, Double> initBalances(List<UUID> memberIds, UUID userId) {
        var balances = new HashMap<UUID, Double>();
        memberIds.stream()
                .filter(memberId -> !memberId.equals(userId))
                .forEach(memberId -> balances.put(memberId, 0.0));
        return balances;
    }

    List<UUID> resolvedSplitMembers(ExpenseEntity expense, List<UUID> allMembers) {
        var splitRefs = expense.getSplitBetween();
        return splitRefs.isEmpty()
                ? allMembers
                : splitRefs.stream().map(ExpenseSplitRef::memberId).toList();
    }

    void applyExpense(ExpenseEntity expense, List<UUID> memberIds, UUID userId, Map<UUID, Double> balances) {
        var splitMembers = resolvedSplitMembers(expense, memberIds);
        if (splitMembers.isEmpty()) return;

        double share = expense.getAmount().doubleValue() / splitMembers.size();
        var paidBy = expense.getPaidBy();

        if (paidBy.equals(userId)) {
            splitMembers.stream()
                    .filter(memberId -> !memberId.equals(userId))
                    .forEach(memberId -> balances.merge(memberId, share, Double::sum));
        } else if (splitMembers.contains(userId)) {
            balances.merge(paidBy, -share, Double::sum);
        }
    }

    void applyConfirmedReimbursements(List<ReimbursementEntity> reimbursements, UUID userId,
                                       Map<UUID, Double> balances) {
        reimbursements.stream()
                .filter(reimbursement -> STATUS_CONFIRMED.equals(reimbursement.getStatus()))
                .forEach(reimbursement -> applyConfirmedReimbursement(reimbursement, userId, balances));
    }

    private void applyConfirmedReimbursement(ReimbursementEntity reimbursement, UUID userId,
                                              Map<UUID, Double> balances) {
        var creditorId = reimbursement.getCreditorId();
        var debtorId = reimbursement.getDebtorId();
        double amount = reimbursement.getAmount().doubleValue();

        if (creditorId.equals(userId) && balances.containsKey(debtorId)) {
            balances.merge(debtorId, -amount, Double::sum);
        } else if (debtorId.equals(userId) && balances.containsKey(creditorId)) {
            balances.merge(creditorId, amount, Double::sum);
        }
    }
}
