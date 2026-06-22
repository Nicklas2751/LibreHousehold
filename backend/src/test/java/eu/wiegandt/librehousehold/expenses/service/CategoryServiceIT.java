package eu.wiegandt.librehousehold.expenses.service;
import eu.wiegandt.librehousehold.expenses.exception.*;
import eu.wiegandt.librehousehold.expenses.mapper.*;
import eu.wiegandt.librehousehold.expenses.model.*;
import eu.wiegandt.librehousehold.expenses.repository.*;

import eu.wiegandt.librehousehold.household.HouseholdQuery;
import eu.wiegandt.librehousehold.model.Category;
import eu.wiegandt.librehousehold.model.CategoryUpdate;
import org.instancio.Instancio;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jdbc.test.autoconfigure.DataJdbcTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.instancio.Select.field;
import static org.mockito.Mockito.doReturn;

@DataJdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({CategoryMapperImpl.class, CategoryService.class, CategoryEntityCallbacks.class})
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@TestPropertySource(properties = {
        "spring.flyway.locations=classpath:db/migration"
})
class CategoryServiceIT {

    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:latest"));

    @Autowired
    private CategoryService categoryService;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ExpenseRepository expenseRepository;

    @Autowired
    private CategoryMapper categoryMapper;

    @MockitoBean
    private HouseholdQuery householdQuery;

    @Nested
    class createCategory {

        @Test
        void validCategory_persistedInDatabase() {
            // given
            var householdId = UUID.randomUUID();
            doReturn(true).when(householdQuery).householdExists(householdId);
            var expected = Instancio.create(Category.class);

            // when
            var result = categoryService.createCategory(householdId, expected);

            // then
            assertThat(result).usingRecursiveComparison().isEqualTo(expected);
        }
    }

    @Nested
    class getCategories {

        @Test
        void existingCategories_returnsAll() {
            // given
            var householdId = UUID.randomUUID();
            var entities = Instancio.ofList(CategoryEntity.class)
                    .set(field(CategoryEntity.class, "householdId"), householdId)
                    .set(field(CategoryEntity.class, "isNew"), true)
                    .create();
            categoryRepository.saveAll(entities);
            var expected = entities.stream().map(categoryMapper::toCategory).toList();

            // when
            var result = categoryService.getCategories(householdId);

            // then
            assertThat(result).containsExactlyInAnyOrderElementsOf(expected);
        }
    }

    @Nested
    class deleteCategory {

        @Test
        void categoryNotInUse_deleted() {
            // given
            var householdId = UUID.randomUUID();
            var categoryId = UUID.randomUUID();
            categoryRepository.save(new CategoryEntity(categoryId, householdId, "Lebensmittel", "🛒"));

            // when
            categoryService.deleteCategory(householdId, categoryId);

            // then
            assertThat(categoryRepository.findById(categoryId)).isEmpty();
        }

        @Test
        void categoryNotFound_throwsCategoryNotFoundException() {
            // given
            var householdId = UUID.randomUUID();
            var categoryId = UUID.randomUUID();

            // when / then
            assertThatThrownBy(() -> categoryService.deleteCategory(householdId, categoryId))
                    .isInstanceOf(CategoryNotFoundException.class);
        }

        @Test
        void categoryInUse_throwsCategoryInUseException() {
            // given
            var householdId = UUID.randomUUID();
            var categoryId = UUID.randomUUID();
            categoryRepository.save(new CategoryEntity(categoryId, householdId, "Lebensmittel", "🛒"));
            expenseRepository.save(new ExpenseEntity(
                    UUID.randomUUID(), householdId, "Einkauf", BigDecimal.TEN,
                    UUID.randomUUID(), LocalDate.now(), categoryId, null, new HashSet<>()
            ));

            // when / then
            assertThatThrownBy(() -> categoryService.deleteCategory(householdId, categoryId))
                    .isInstanceOf(CategoryInUseException.class);
        }
    }

    @Nested
    class updateCategory {

        @Test
        void existingCategory_updatesNameAndIcon() {
            // given
            var householdId = UUID.randomUUID();
            var categoryId = UUID.randomUUID();
            categoryRepository.save(new CategoryEntity(categoryId, householdId, "Alt", null));
            var update = new CategoryUpdate().name("Neu").icon("🥦");
            var expected = new Category().id(categoryId).name("Neu").icon("🥦");

            // when
            var result = categoryService.updateCategory(householdId, categoryId, update);

            // then
            assertThat(result).usingRecursiveComparison().isEqualTo(expected);
        }

        @Test
        void categoryNotFound_throwsCategoryNotFoundException() {
            // given
            var householdId = UUID.randomUUID();
            var categoryId = UUID.randomUUID();
            var update = new CategoryUpdate().name("Neu");

            // when / then
            assertThatThrownBy(() -> categoryService.updateCategory(householdId, categoryId, update))
                    .isInstanceOf(CategoryNotFoundException.class);
        }

        @Test
        void duplicateName_throwsCategoryAlreadyExistsException() {
            // given
            var householdId = UUID.randomUUID();
            var categoryId = UUID.randomUUID();
            var otherCategoryId = UUID.randomUUID();
            categoryRepository.save(new CategoryEntity(categoryId, householdId, "Original", null));
            categoryRepository.save(new CategoryEntity(otherCategoryId, householdId, "Belegt", null));
            var update = new CategoryUpdate().name("Belegt");

            // when / then
            assertThatThrownBy(() -> categoryService.updateCategory(householdId, categoryId, update))
                    .isInstanceOf(CategoryAlreadyExistsException.class);
        }
    }
}
