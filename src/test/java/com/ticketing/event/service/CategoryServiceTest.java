package com.ticketing.event.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import com.ticketing.event.dto.CategoryResponse;
import com.ticketing.event.dto.CreateCategoryRequest;
import com.ticketing.event.model.Category;
import com.ticketing.event.repository.CategoryRepository;

import jakarta.persistence.EntityNotFoundException;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock
    private CategoryRepository categoryRepository;

    @InjectMocks
    private CategoryService categoryService;

    @Test
    @DisplayName("createCategory with valid data should persist and return")
    void createCategory_withValidData_shouldPersistAndReturn() {
        CreateCategoryRequest request = CreateCategoryRequest.builder()
                .name("Music")
                .description("Live concerts and tours")
                .iconUrl("music-note")
                .build();

        when(categoryRepository.save(any(Category.class))).thenAnswer(invocation -> {
            Category category = invocation.getArgument(0);
            category.setId(7L);
            return category;
        });

        CategoryResponse response = categoryService.createCategory(request);

        assertEquals(7L, response.getId());
        assertEquals("Music", response.getName());
        assertEquals("music-note", response.getIconUrl());
        verify(categoryRepository).save(any(Category.class));
    }

    @Test
    @DisplayName("getCategory with invalid id should throw not found exception")
    void getCategory_withInvalidId_shouldThrowNotFoundException() {
        when(categoryRepository.findById(404L)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> categoryService.getCategoryById(404L));
    }

    @Test
    @DisplayName("listCategories should return paginated result")
    void listCategories_shouldReturnPaginatedResult() {
        Pageable pageable = PageRequest.of(0, 2);
        Category categoryOne = Category.builder()
                .id(1L)
                .name("Music")
                .description("Live concerts and tours")
                .iconUrl("music-note")
                .build();
        Category categoryTwo = Category.builder()
                .id(2L)
                .name("Sports")
                .description("Matches and tournaments")
                .iconUrl("trophy")
                .build();
        when(categoryRepository.findAll(pageable)).thenReturn(new PageImpl<>(List.of(categoryOne, categoryTwo)));

        Page<CategoryResponse> response = categoryService.listCategories(pageable);

        assertEquals(2, response.getTotalElements());
        assertEquals("Music", response.getContent().getFirst().getName());
        assertEquals("Sports", response.getContent().get(1).getName());
    }

    @Test
    @DisplayName("updateCategory with valid data should persist changes")
    void updateCategory_withValidData_shouldPersistChanges() {
        CreateCategoryRequest request = CreateCategoryRequest.builder()
                .name("Technology")
                .description("Conferences and meetups")
                .iconUrl("laptop")
                .build();

        Category existingCategory = Category.builder()
                .id(7L)
                .name("Music")
                .description("Live concerts and tours")
                .iconUrl("music-note")
                .build();

        when(categoryRepository.findById(7L)).thenReturn(Optional.of(existingCategory));
        when(categoryRepository.save(existingCategory)).thenReturn(existingCategory);

        CategoryResponse response = categoryService.updateCategory(7L, request);

        assertEquals(7L, response.getId());
        assertEquals("Technology", response.getName());
        assertEquals("Conferences and meetups", response.getDescription());
        assertEquals("laptop", response.getIconUrl());
        verify(categoryRepository).findById(7L);
        verify(categoryRepository).save(existingCategory);
    }

    @Test
    @DisplayName("updateCategory with invalid id should throw not found exception")
    void updateCategory_withInvalidId_shouldThrowNotFoundException() {
        CreateCategoryRequest request = CreateCategoryRequest.builder()
                .name("Technology")
                .description("Conferences and meetups")
                .iconUrl("laptop")
                .build();

        when(categoryRepository.findById(404L)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> categoryService.updateCategory(404L, request));
        verify(categoryRepository).findById(404L);
        verify(categoryRepository, never()).save(any(Category.class));
    }

    @Test
    @DisplayName("deleteCategory with valid id should remove category")
    void deleteCategory_withValidId_shouldRemoveCategory() {
        Category existingCategory = Category.builder()
                .id(7L)
                .name("Music")
                .description("Live concerts and tours")
                .iconUrl("music-note")
                .build();

        when(categoryRepository.findById(7L)).thenReturn(Optional.of(existingCategory));

        categoryService.deleteCategory(7L);

        verify(categoryRepository).findById(7L);
        verify(categoryRepository).deleteById(7L);
    }

    @Test
    @DisplayName("deleteCategory with invalid id should throw not found exception")
    void deleteCategory_withInvalidId_shouldThrowNotFoundException() {
        when(categoryRepository.findById(404L)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> categoryService.deleteCategory(404L));
        verify(categoryRepository).findById(404L);
        verify(categoryRepository, never()).deleteById(eq(404L));
    }
}
