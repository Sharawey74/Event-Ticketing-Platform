package com.ticketing.event.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ticketing.common.dto.ApiResponse;
import com.ticketing.event.dto.CategoryResponse;
import com.ticketing.event.dto.CreateCategoryRequest;
import com.ticketing.event.service.CategoryService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/categories")
@Validated
@RequiredArgsConstructor
@Tag(name = "Categories")
public class CategoryController {

    private static final Logger logger = LoggerFactory.getLogger(CategoryController.class);

    private final CategoryService categoryService;

    @Operation(summary = "Create a category", description = "ADMIN only.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Category created")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Caller is not an ADMIN")
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<CategoryResponse>> createCategory(
            @Valid @RequestBody CreateCategoryRequest request) {
        CategoryResponse response = categoryService.createCategory(request);
        logger.info("Create category endpoint finished for category {}", response.getId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "Get category details", description = "Publicly viewable.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Category found")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Category not found")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CategoryResponse>> getCategory(@PathVariable Long id) {
        CategoryResponse response = categoryService.getCategoryById(id);
        logger.info("Get category endpoint finished for category {}", id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "List categories", description = "Publicly viewable paginated category list.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Page of categories")
    @GetMapping
    public ResponseEntity<ApiResponse<Page<CategoryResponse>>> listCategories(Pageable pageable) {
        Page<CategoryResponse> response = categoryService.listCategories(pageable);
        logger.info("List categories endpoint finished with page {}", pageable.getPageNumber());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "Update a category", description = "ADMIN only.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Category updated")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Caller is not an ADMIN")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Category not found")
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<CategoryResponse>> updateCategory(
            @PathVariable Long id,
            @Valid @RequestBody com.ticketing.event.dto.UpdateCategoryRequest request) {
        CategoryResponse response = categoryService.updateCategory(id, request);
        logger.info("Update category endpoint finished for category {}", id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "Delete a category", description = "ADMIN only.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Category deleted")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Caller is not an ADMIN")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Category not found")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteCategory(@PathVariable Long id) {
        categoryService.deleteCategory(id);
        logger.info("Delete category endpoint finished for category {}", id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
