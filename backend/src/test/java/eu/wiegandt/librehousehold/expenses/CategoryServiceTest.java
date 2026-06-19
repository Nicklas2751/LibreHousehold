package eu.wiegandt.librehousehold.expenses;

import eu.wiegandt.librehousehold.household.HouseholdQuery;
import eu.wiegandt.librehousehold.model.Category;
import org.instancio.Instancio;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock
    private CategoryRepository categoryRepository;

    @Spy
    private CategoryMapper categoryMapper = Mappers.getMapper(CategoryMapper.class);

    @Mock
    private HouseholdQuery householdQuery;

    @InjectMocks
    private CategoryService categoryService;

    @Nested
    class getCategories {

        @Test
        void existingHousehold_returnsCategories() {
            // given
            var householdId = UUID.randomUUID();
            var entity = Instancio.create(CategoryEntity.class);
            doReturn(List.of(entity)).when(categoryRepository).findByHouseholdId(householdId);
            var expected = categoryMapper.toCategory(entity);

            // when
            var result = categoryService.getCategories(householdId);

            // then
            assertThat(result).singleElement().usingRecursiveComparison().isEqualTo(expected);
        }

        @Test
        void noCategories_returnsEmptyList() {
            // given
            var householdId = UUID.randomUUID();
            doReturn(List.of()).when(categoryRepository).findByHouseholdId(householdId);

            // when
            var result = categoryService.getCategories(householdId);

            // then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    class createCategory {

        @Test
        void newName_returnsCreatedCategory() {
            // given
            var householdId = UUID.randomUUID();
            var category = Instancio.create(Category.class);
            var savedEntity = Instancio.create(CategoryEntity.class);
            doReturn(true).when(householdQuery).householdExists(householdId);
            doReturn(false).when(categoryRepository).existsByHouseholdIdAndName(householdId, category.getName());
            doReturn(savedEntity).when(categoryRepository).save(any(CategoryEntity.class));
            var expected = categoryMapper.toCategory(savedEntity);

            // when
            var result = categoryService.createCategory(householdId, category);

            // then
            assertThat(result).usingRecursiveComparison().isEqualTo(expected);
        }

        @Test
        void duplicateName_throwsCategoryAlreadyExistsException() {
            // given
            var householdId = UUID.randomUUID();
            var category = Instancio.create(Category.class);
            doReturn(true).when(householdQuery).householdExists(householdId);
            doReturn(true).when(categoryRepository).existsByHouseholdIdAndName(householdId, category.getName());

            // when / then
            assertThatThrownBy(() -> categoryService.createCategory(householdId, category))
                    .isInstanceOf(CategoryAlreadyExistsException.class);
        }

        @Test
        void unknownHousehold_throwsHouseholdNotFoundException() {
            // given
            var householdId = UUID.randomUUID();
            var category = Instancio.create(Category.class);
            doReturn(false).when(householdQuery).householdExists(householdId);

            // when / then
            assertThatThrownBy(() -> categoryService.createCategory(householdId, category))
                    .isInstanceOf(HouseholdNotFoundException.class);
        }
    }
}
