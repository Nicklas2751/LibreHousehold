package eu.wiegandt.librehousehold.expenses.mapper;
import eu.wiegandt.librehousehold.expenses.model.*;

import eu.wiegandt.librehousehold.model.Expense;
import eu.wiegandt.librehousehold.model.ExpenseUpdate;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ExpenseMapperTest {

    private final ExpenseMapper mapper = Mappers.getMapper(ExpenseMapper.class);

    @Nested
    class toExpense {

        @Test
        void entityWithAllFields_mapsAllFieldsCorrectly() {
            // given
            var id = UUID.randomUUID();
            var householdId = UUID.randomUUID();
            var paidBy = UUID.randomUUID();
            var categoryId = UUID.randomUUID();
            var memberId = UUID.randomUUID();
            var entity = new ExpenseEntity(id, householdId, "Groceries", BigDecimal.valueOf(50.0),
                    paidBy, LocalDate.of(2026, 1, 15), categoryId, "Weekly shopping",
                    Set.of(new ExpenseSplitRef(memberId)));
            var expected = new Expense(id, "Groceries", 50.0, paidBy,
                    LocalDate.of(2026, 1, 15), categoryId)
                    .notes("Weekly shopping")
                    .isMutable(true)
                    .splitBetween(List.of(memberId));

            // when
            var result = mapper.toExpense(entity, true);

            // then
            assertThat(result).usingRecursiveComparison().isEqualTo(expected);
        }

        @Test
        void entityWithNullNotes_mapsToEmptyOptional() {
            // given
            var id = UUID.randomUUID();
            var paidBy = UUID.randomUUID();
            var categoryId = UUID.randomUUID();
            var entity = new ExpenseEntity(id, UUID.randomUUID(), "Rent",
                    BigDecimal.valueOf(800.0), paidBy, LocalDate.of(2026, 2, 1),
                    categoryId, null, new HashSet<>());
            var expected = new Expense(id, "Rent", 800.0, paidBy, LocalDate.of(2026, 2, 1), categoryId)
                    .isMutable(true);

            // when
            var result = mapper.toExpense(entity, true);

            // then
            assertThat(result).usingRecursiveComparison().isEqualTo(expected);
        }

        @Test
        void entityWithEmptySplitBetween_mapsToEmptyList() {
            // given
            var id = UUID.randomUUID();
            var paidBy = UUID.randomUUID();
            var categoryId = UUID.randomUUID();
            var entity = new ExpenseEntity(id, UUID.randomUUID(), "Dinner",
                    BigDecimal.valueOf(30.0), paidBy, LocalDate.of(2026, 3, 10),
                    categoryId, null, new HashSet<>());
            var expected = new Expense(id, "Dinner", 30.0, paidBy, LocalDate.of(2026, 3, 10), categoryId)
                    .isMutable(false);

            // when
            var result = mapper.toExpense(entity, false);

            // then
            assertThat(result).usingRecursiveComparison().isEqualTo(expected);
        }
    }

    @Nested
    class toEntity {

        @Test
        void expenseWithAllFields_mapsAllFieldsCorrectly() {
            // given
            var id = UUID.randomUUID();
            var householdId = UUID.randomUUID();
            var paidBy = UUID.randomUUID();
            var categoryId = UUID.randomUUID();
            var memberId = UUID.randomUUID();
            var expense = new Expense(id, "Groceries", 50.0, paidBy,
                    LocalDate.of(2026, 1, 15), categoryId)
                    .notes("Weekly shopping")
                    .splitBetween(List.of(memberId));
            var expected = new ExpenseEntity(id, householdId, "Groceries", BigDecimal.valueOf(50.0),
                    paidBy, LocalDate.of(2026, 1, 15), categoryId, "Weekly shopping",
                    Set.of(new ExpenseSplitRef(memberId)));

            // when
            var result = mapper.toEntity(expense, householdId);

            // then
            assertThat(result).usingRecursiveComparison()
                    .ignoringFields("isNew")
                    .isEqualTo(expected);
        }

        @Test
        void expenseWithEmptyOptionalNotes_mapsToNullEntityNotes() {
            // given
            var id = UUID.randomUUID();
            var householdId = UUID.randomUUID();
            var paidBy = UUID.randomUUID();
            var categoryId = UUID.randomUUID();
            var expense = new Expense(id, "Rent", 800.0, paidBy, LocalDate.of(2026, 2, 1), categoryId);
            var expected = new ExpenseEntity(id, householdId, "Rent", BigDecimal.valueOf(800.0),
                    paidBy, LocalDate.of(2026, 2, 1), categoryId, null, new HashSet<>());

            // when
            var result = mapper.toEntity(expense, householdId);

            // then
            assertThat(result).usingRecursiveComparison()
                    .ignoringFields("isNew")
                    .isEqualTo(expected);
        }
    }

    @Nested
    class updateEntityFromUpdate {

        @Test
        void presentTitle_updatesEntityTitle() {
            // given
            var entity = new ExpenseEntity(UUID.randomUUID(), UUID.randomUUID(), "Old Title",
                    BigDecimal.valueOf(50.0), UUID.randomUUID(), LocalDate.of(2026, 1, 15),
                    UUID.randomUUID(), null, new HashSet<>());
            var update = new ExpenseUpdate().title("New Title");

            // when
            mapper.updateEntityFromUpdate(update, entity);

            // then
            assertThat(entity.getTitle()).isEqualTo("New Title");
        }

        @Test
        void emptyTitle_doesNotUpdateEntityTitle() {
            // given
            var entity = new ExpenseEntity(UUID.randomUUID(), UUID.randomUUID(), "Original Title",
                    BigDecimal.valueOf(50.0), UUID.randomUUID(), LocalDate.of(2026, 1, 15),
                    UUID.randomUUID(), null, new HashSet<>());
            var update = new ExpenseUpdate();

            // when
            mapper.updateEntityFromUpdate(update, entity);

            // then
            assertThat(entity.getTitle()).isEqualTo("Original Title");
        }

        @Test
        void presentAmount_convertsToDecimalAndUpdatesEntity() {
            // given
            var entity = new ExpenseEntity(UUID.randomUUID(), UUID.randomUUID(), "Dinner",
                    BigDecimal.valueOf(30.0), UUID.randomUUID(), LocalDate.of(2026, 1, 15),
                    UUID.randomUUID(), null, new HashSet<>());
            var update = new ExpenseUpdate().amount(99.99);

            // when
            mapper.updateEntityFromUpdate(update, entity);

            // then
            assertThat(entity.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(99.99));
        }

        @Test
        void emptyAmount_doesNotUpdateEntityAmount() {
            // given
            var originalAmount = BigDecimal.valueOf(30.0);
            var entity = new ExpenseEntity(UUID.randomUUID(), UUID.randomUUID(), "Dinner",
                    originalAmount, UUID.randomUUID(), LocalDate.of(2026, 1, 15),
                    UUID.randomUUID(), null, new HashSet<>());
            var update = new ExpenseUpdate();

            // when
            mapper.updateEntityFromUpdate(update, entity);

            // then
            assertThat(entity.getAmount()).isEqualByComparingTo(originalAmount);
        }

        @Test
        void presentSplitBetween_updatesSplitBetween() {
            // given
            var memberA = UUID.randomUUID();
            var memberB = UUID.randomUUID();
            var entity = new ExpenseEntity(UUID.randomUUID(), UUID.randomUUID(), "Dinner",
                    BigDecimal.valueOf(30.0), UUID.randomUUID(), LocalDate.of(2026, 1, 15),
                    UUID.randomUUID(), null, new HashSet<>(Set.of(new ExpenseSplitRef(memberA))));
            var update = new ExpenseUpdate().splitBetween(List.of(memberB));

            // when
            mapper.updateEntityFromUpdate(update, entity);

            // then
            assertThat(entity.getSplitBetween())
                    .containsExactly(new ExpenseSplitRef(memberB));
        }
    }

    @Nested
    class toOptionalString {

        @Test
        void nonNullValue_returnsOptionalContainingValue() {
            assertThat(mapper.toOptionalString("hello")).contains("hello");
        }

        @Test
        void nullValue_returnsEmptyOptional() {
            assertThat(mapper.toOptionalString(null)).isEmpty();
        }
    }

    @Nested
    class fromOptionalString {

        @Test
        void presentOptional_returnsValue() {
            assertThat(mapper.fromOptionalString(Optional.of("hello"))).isEqualTo("hello");
        }

        @Test
        void emptyOptional_returnsNull() {
            assertThat(mapper.fromOptionalString(Optional.empty())).isNull();
        }
    }

    @Nested
    class toUuidList {

        @Test
        void refsSet_returnsMappedUuidList() {
            // given
            var memberId = UUID.randomUUID();
            var refs = Set.of(new ExpenseSplitRef(memberId));

            // when
            var result = mapper.toUuidList(refs);

            // then
            assertThat(result).containsExactly(memberId);
        }

        @Test
        void nullRefs_returnsEmptyList() {
            assertThat(mapper.toUuidList(null)).isEmpty();
        }
    }

    @Nested
    class toExpenseSplitRefSet {

        @Test
        void uuidList_returnsMappedRefSet() {
            // given
            var memberId = UUID.randomUUID();

            // when
            var result = mapper.toExpenseSplitRefSet(List.of(memberId));

            // then
            assertThat(result).containsExactly(new ExpenseSplitRef(memberId));
        }

        @Test
        void nullList_returnsEmptySet() {
            assertThat(mapper.toExpenseSplitRefSet(null)).isEmpty();
        }
    }

    @Nested
    class optionalDoubleToDecimal {

        @Test
        void presentOptional_returnsBigDecimal() {
            assertThat(mapper.optionalDoubleToDecimal(Optional.of(42.5)))
                    .isEqualByComparingTo(BigDecimal.valueOf(42.5));
        }

        @Test
        void emptyOptional_returnsNull() {
            assertThat(mapper.optionalDoubleToDecimal(Optional.empty())).isNull();
        }

        @Test
        void nullOptional_returnsNull() {
            assertThat(mapper.optionalDoubleToDecimal(null)).isNull();
        }
    }
}
