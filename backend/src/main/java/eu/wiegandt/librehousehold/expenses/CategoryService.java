package eu.wiegandt.librehousehold.expenses;

import eu.wiegandt.librehousehold.household.HouseholdQuery;
import eu.wiegandt.librehousehold.model.Category;
import eu.wiegandt.librehousehold.model.CategoryUpdate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
class CategoryService {

    private final CategoryRepository categoryRepository;
    private final ExpenseRepository expenseRepository;
    private final CategoryMapper categoryMapper;
    private final HouseholdQuery householdQuery;

    CategoryService(CategoryRepository categoryRepository,
                    ExpenseRepository expenseRepository,
                    CategoryMapper categoryMapper,
                    HouseholdQuery householdQuery) {
        this.categoryRepository = categoryRepository;
        this.expenseRepository = expenseRepository;
        this.categoryMapper = categoryMapper;
        this.householdQuery = householdQuery;
    }

    List<Category> getCategories(UUID householdId) {
        return categoryRepository.findByHouseholdId(householdId).stream()
                .map(categoryMapper::toCategory)
                .toList();
    }

    Category createCategory(UUID householdId, Category category) {
        if (!householdQuery.householdExists(householdId)) {
            throw new HouseholdNotFoundException();
        }
        if (categoryRepository.existsByHouseholdIdAndName(householdId, category.getName())) {
            throw new CategoryAlreadyExistsException();
        }
        var saved = categoryRepository.save(categoryMapper.toEntity(category, householdId));
        return categoryMapper.toCategory(saved);
    }

    Category updateCategory(UUID householdId, UUID categoryId, CategoryUpdate update) {
        var entity = categoryRepository.findByIdAndHouseholdId(categoryId, householdId)
                .orElseThrow(CategoryNotFoundException::new);
        update.getName().ifPresent(name -> {
            if (categoryRepository.existsByHouseholdIdAndNameAndIdNot(householdId, name, categoryId)) {
                throw new CategoryAlreadyExistsException();
            }
        });
        categoryMapper.updateEntityFromUpdate(update, entity);
        var saved = categoryRepository.save(entity);
        return categoryMapper.toCategory(saved);
    }

    void deleteCategory(UUID householdId, UUID categoryId) {
        categoryRepository.findByIdAndHouseholdId(categoryId, householdId)
                .orElseThrow(CategoryNotFoundException::new);
        if (expenseRepository.existsByCategoryId(categoryId)) {
            throw new CategoryInUseException();
        }
        categoryRepository.deleteById(categoryId);
    }
}
