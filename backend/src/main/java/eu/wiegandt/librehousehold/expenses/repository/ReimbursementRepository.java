package eu.wiegandt.librehousehold.expenses.repository;

import eu.wiegandt.librehousehold.expenses.model.ReimbursementEntity;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReimbursementRepository extends CrudRepository<ReimbursementEntity, UUID> {

    List<ReimbursementEntity> findByHouseholdId(UUID householdId);

    Optional<ReimbursementEntity> findByIdAndHouseholdId(UUID id, UUID householdId);

    @Query("""
            SELECT EXISTS (
                SELECT 1 FROM expenses.settlement_expense se
                JOIN expenses.reimbursement r ON r.id = se.settlement_id
                WHERE se.expense_id = :expenseId
                  AND r.household_id = :householdId
                  AND r.status IN ('PENDING', 'CONFIRMED')
            )
            """)
    boolean existsActiveSettlementCoveringExpense(@Param("householdId") UUID householdId,
                                                   @Param("expenseId") UUID expenseId);

    void deleteByHouseholdId(UUID householdId);
}
