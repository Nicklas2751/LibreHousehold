package eu.wiegandt.librehousehold.expenses;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface ReimbursementRepository extends CrudRepository<ReimbursementEntity, UUID> {

    List<ReimbursementEntity> findByHouseholdId(UUID householdId);

    Optional<ReimbursementEntity> findByIdAndHouseholdId(UUID id, UUID householdId);

    boolean existsByHouseholdIdAndCreditorIdAndStatusIn(UUID householdId, UUID creditorId, List<String> statuses);

    void deleteByHouseholdId(UUID householdId);

    @Query("""
            SELECT EXISTS (
                SELECT 1 FROM expenses.reimbursement r
                WHERE r.household_id = :householdId
                  AND r.status IN ('PENDING', 'CONFIRMED')
                  AND r.debtor_id = :debtorId
                  AND (
                    NOT EXISTS (SELECT 1 FROM expenses.expense_split es WHERE es.expense_id = :expenseId)
                    OR EXISTS (SELECT 1 FROM expenses.expense_split es
                               WHERE es.expense_id = :expenseId AND es.member_id = r.creditor_id)
                  )
            )
            """)
    boolean existsActiveSettlementAsDebtorCoveringExpense(@Param("householdId") UUID householdId,
                                                          @Param("debtorId") UUID debtorId,
                                                          @Param("expenseId") UUID expenseId);
}
