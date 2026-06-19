package eu.wiegandt.librehousehold.expenses;

import eu.wiegandt.librehousehold.household.HouseholdQuery;
import eu.wiegandt.librehousehold.model.Category;
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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.instancio.Select.field;
import static org.mockito.Mockito.doReturn;

@DataJdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({CategoryMapperImpl.class, CategoryService.class})
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
}
