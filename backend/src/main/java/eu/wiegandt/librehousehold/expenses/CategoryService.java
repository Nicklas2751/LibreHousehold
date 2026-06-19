package eu.wiegandt.librehousehold.expenses;

import eu.wiegandt.librehousehold.household.HouseholdQuery;
import eu.wiegandt.librehousehold.model.Category;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
class CategoryService {

    private final CategoryRepository categoryRepository;
    private final CategoryMapper categoryMapper;
    private final HouseholdQuery householdQuery;

    CategoryService(CategoryRepository categoryRepository,
                    CategoryMapper categoryMapper,
                    HouseholdQuery householdQuery) {
        this.categoryRepository = categoryRepository;
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
}
