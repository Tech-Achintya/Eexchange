package com.eexchange.backend.Service;

import com.eexchange.backend.DTO.CategoryResponse;
import com.eexchange.backend.Entity.Category;
import com.eexchange.backend.Repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;

    @Transactional(readOnly = true)
    public List<CategoryResponse> getActiveCategories() {
        return categoryRepository.findByIsActiveTrue().stream()
                .sorted(Comparator.comparing(Category::getName, String.CASE_INSENSITIVE_ORDER))
                .map(category -> new CategoryResponse(
                        category.getId(),
                        category.getName(),
                        category.getSlug()))
                .toList();
    }
}