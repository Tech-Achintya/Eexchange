package com.eexchange.backend.Controller;

import com.eexchange.backend.DTO.ApiResponse;
import com.eexchange.backend.DTO.CategoryResponse;
import com.eexchange.backend.Service.CategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<CategoryResponse>>> getCategories() {
        return ResponseEntity.ok(ApiResponse.ok(
                "Categories retrieved",
                categoryService.getActiveCategories()));
    }
}