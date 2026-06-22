package eu.wiegandt.librehousehold.expenses.mapper;
import eu.wiegandt.librehousehold.expenses.model.*;

import eu.wiegandt.librehousehold.model.Category;
import eu.wiegandt.librehousehold.model.CategoryUpdate;
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

    @Nested
    class updateEntityFromUpdate {

        @Test
        void bothFieldsPresent_updatesNameAndIcon() {
            // given
            var id = UUID.randomUUID();
            var householdId = UUID.randomUUID();
            var entity = new CategoryEntity(id, householdId, "Old Name", "🛒");
            var update = new CategoryUpdate().name("New Name").icon("🥦");
            var expected = new CategoryEntity(id, householdId, "New Name", "🥦");

            // when
            mapper.updateEntityFromUpdate(update, entity);

            // then
            assertThat(entity).usingRecursiveComparison().ignoringFields("isNew").isEqualTo(expected);
        }

        @Test
        void onlyNamePresent_updatesNameLeavesIconUnchanged() {
            // given
            var id = UUID.randomUUID();
            var householdId = UUID.randomUUID();
            var entity = new CategoryEntity(id, householdId, "Old Name", "🛒");
            var update = new CategoryUpdate().name("New Name");
            var expected = new CategoryEntity(id, householdId, "New Name", "🛒");

            // when
            mapper.updateEntityFromUpdate(update, entity);

            // then
            assertThat(entity).usingRecursiveComparison().ignoringFields("isNew").isEqualTo(expected);
        }

        @Test
        void onlyIconPresent_updatesIconLeavesNameUnchanged() {
            // given
            var id = UUID.randomUUID();
            var householdId = UUID.randomUUID();
            var entity = new CategoryEntity(id, householdId, "Old Name", "🛒");
            var update = new CategoryUpdate().icon("🥦");
            var expected = new CategoryEntity(id, householdId, "Old Name", "🥦");

            // when
            mapper.updateEntityFromUpdate(update, entity);

            // then
            assertThat(entity).usingRecursiveComparison().ignoringFields("isNew").isEqualTo(expected);
        }

        @Test
        void bothFieldsEmpty_nothingUpdated() {
            // given
            var id = UUID.randomUUID();
            var householdId = UUID.randomUUID();
            var entity = new CategoryEntity(id, householdId, "Old Name", "🛒");
            var update = new CategoryUpdate();
            var expected = new CategoryEntity(id, householdId, "Old Name", "🛒");

            // when
            mapper.updateEntityFromUpdate(update, entity);

            // then
            assertThat(entity).usingRecursiveComparison().ignoringFields("isNew").isEqualTo(expected);
        }
    }
}
