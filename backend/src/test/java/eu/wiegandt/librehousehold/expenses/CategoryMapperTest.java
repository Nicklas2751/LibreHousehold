package eu.wiegandt.librehousehold.expenses;

import eu.wiegandt.librehousehold.model.Category;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CategoryMapperTest {

    private final CategoryMapper mapper = Mappers.getMapper(CategoryMapper.class);

    @Nested
    class toCategory {

        @Test
        void entityWithIcon_mapsAllFieldsCorrectly() {
            // given
            var id = UUID.randomUUID();
            var householdId = UUID.randomUUID();
            var entity = new CategoryEntity(id, householdId, "Groceries", "🛒");
            var expected = new Category(id, "Groceries").icon("🛒");

            // when
            var result = mapper.toCategory(entity);

            // then
            assertThat(result).usingRecursiveComparison().isEqualTo(expected);
        }

        @Test
        void entityWithoutIcon_mapsToEmptyOptionalIcon() {
            // given
            var id = UUID.randomUUID();
            var householdId = UUID.randomUUID();
            var entity = new CategoryEntity(id, householdId, "Transport", null);
            var expected = new Category(id, "Transport");

            // when
            var result = mapper.toCategory(entity);

            // then
            assertThat(result).usingRecursiveComparison().isEqualTo(expected);
        }
    }

    @Nested
    class toEntity {

        @Test
        void categoryWithIcon_mapsAllFieldsCorrectly() {
            // given
            var id = UUID.randomUUID();
            var householdId = UUID.randomUUID();
            var category = new Category(id, "Groceries").icon("🛒");
            var expected = new CategoryEntity(id, householdId, "Groceries", "🛒");

            // when
            var result = mapper.toEntity(category, householdId);

            // then
            assertThat(result).usingRecursiveComparison()
                    .ignoringFields("isNew")
                    .isEqualTo(expected);
        }

        @Test
        void categoryWithEmptyIcon_mapsToNullEntityIcon() {
            // given
            var id = UUID.randomUUID();
            var householdId = UUID.randomUUID();
            var category = new Category(id, "Transport");
            var expected = new CategoryEntity(id, householdId, "Transport", null);

            // when
            var result = mapper.toEntity(category, householdId);

            // then
            assertThat(result).usingRecursiveComparison()
                    .ignoringFields("isNew")
                    .isEqualTo(expected);
        }
    }

    @Nested
    class toOptionalString {

        @Test
        void nonNullValue_returnsOptionalContainingValue() {
            assertThat(mapper.toOptionalString("🛒")).contains("🛒");
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
            assertThat(mapper.fromOptionalString(Optional.of("🛒"))).isEqualTo("🛒");
        }

        @Test
        void emptyOptional_returnsNull() {
            assertThat(mapper.fromOptionalString(Optional.empty())).isNull();
        }
    }
}
