package eu.wiegandt.librehousehold.expenses;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface ExpenseRepository extends CrudRepository<ExpenseEntity, UUID> {

    List<ExpenseEntity> findByHouseholdId(UUID householdId);

    List<ExpenseEntity> findByHouseholdIdOrderByDateDesc(UUID householdId);

    boolean existsByCategoryId(UUID categoryId);

    Optional<ExpenseEntity> findByIdAndHouseholdId(UUID id, UUID householdId);

    @Query("""
            SELECT DISTINCT e.* FROM expenses.expense e
            WHERE e.household_id = :householdId
              AND e.paid_by = :payerId
              AND (
                NOT EXISTS (SELECT 1 FROM expenses.expense_split es WHERE es.expense_id = e.id)
                OR EXISTS (SELECT 1 FROM expenses.expense_split es WHERE es.expense_id = e.id AND es.member_id = :debtorId)
              )
              AND e.date >= COALESCE(
                (SELECT MAX(r.created_at)::date
                 FROM expenses.reimbursement r
                 WHERE r.household_id = :householdId
                   AND ((r.creditor_id = :payerId AND r.debtor_id = :debtorId)
                     OR (r.creditor_id = :debtorId AND r.debtor_id = :payerId))
                   AND r.status = 'CONFIRMED'),
                '0001-01-01'::date
              )
            """)
    List<ExpenseEntity> findDebtorExpenses(@Param("householdId") UUID householdId,
                                           @Param("payerId") UUID payerId,
                                           @Param("debtorId") UUID debtorId);
}
