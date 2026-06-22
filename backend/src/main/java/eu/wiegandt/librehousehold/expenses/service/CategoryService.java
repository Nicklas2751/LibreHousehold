package eu.wiegandt.librehousehold.expenses.service;

import eu.wiegandt.librehousehold.expenses.exception.CategoryAlreadyExistsException;
import eu.wiegandt.librehousehold.expenses.exception.CategoryInUseException;
import eu.wiegandt.librehousehold.expenses.exception.CategoryNotFoundException;
import eu.wiegandt.librehousehold.expenses.exception.HouseholdNotFoundException;
import eu.wiegandt.librehousehold.expenses.mapper.CategoryMapper;
import eu.wiegandt.librehousehold.expenses.repository.CategoryRepository;
import eu.wiegandt.librehousehold.expenses.repository.ExpenseRepository;
import eu.wiegandt.librehousehold.household.HouseholdQuery;
import eu.wiegandt.librehousehold.model.Category;
import eu.wiegandt.librehousehold.model.CategoryUpdate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final ExpenseRepository expenseRepository;
    private final CategoryMapper categoryMapper;
    private final HouseholdQuery householdQuery;

    public CategoryService(CategoryRepository categoryRepository,
                    ExpenseRepository expenseRepository,
                    CategoryMapper categoryMapper,
                    HouseholdQuery householdQuery) {
        this.categoryRepository = categoryRepository;
        this.expenseRepository = expenseRepository;
        this.categoryMapper = categoryMapper;
        this.householdQuery = householdQuery;
    }

    public List<Category> getCategories(UUID householdId) {
        return categoryRepository.findByHouseholdId(householdId).stream()
                .map(categoryMapper::toCategory)
                .toList();
    }

    public Category createCategory(UUID householdId, Category category) {
        if (!householdQuery.householdExists(householdId)) {
            throw new HouseholdNotFoundException();
        }
        if (categoryRepository.existsByHouseholdIdAndName(householdId, category.getName())) {
            throw new CategoryAlreadyExistsException();
        }
        var saved = categoryRepository.save(categoryMapper.toEntity(category, householdId));
        return categoryMapper.toCategory(saved);
    }

    public Category updateCategory(UUID householdId, UUID categoryId, CategoryUpdate update) {
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

    public void deleteCategory(UUID householdId, UUID categoryId) {
        categoryRepository.findByIdAndHouseholdId(categoryId, householdId)
                .orElseThrow(CategoryNotFoundException::new);
        if (expenseRepository.existsByCategoryId(categoryId)) {
            throw new CategoryInUseException();
        }
        categoryRepository.deleteById(categoryId);
    }
}
