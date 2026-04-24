package com.ticketing.event.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketing.common.config.TestSecurityConfig;
import com.ticketing.event.dto.CategoryResponse;
import com.ticketing.event.dto.CreateCategoryRequest;
import com.ticketing.event.service.CategoryService;

@WebMvcTest(controllers = CategoryController.class)
@Import(TestSecurityConfig.class)
class CategoryControllerTest {

        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private ObjectMapper objectMapper;

        @MockitoBean
        private CategoryService categoryService;

        @MockitoBean
        private com.ticketing.common.security.JwtService jwtService;

        // ─── ADMIN happy-path ────────────────────────────────────────────────────

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("POST /api/categories as ADMIN should create category and return 200")
        void createCategory_asAdmin_shouldReturn200() throws Exception {
                CreateCategoryRequest request = CreateCategoryRequest.builder()
                                .name("Music")
                                .description("Live concerts and tours")
                                .iconUrl("music-note")
                                .build();

                CategoryResponse response = CategoryResponse.builder()
                                .id(7L)
                                .name("Music")
                                .description("Live concerts and tours")
                                .iconUrl("music-note")
                                .build();

                when(categoryService.createCategory(any(CreateCategoryRequest.class))).thenReturn(response);

                mockMvc.perform(post("/api/categories").with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.success").value(true))
                                .andExpect(jsonPath("$.data.id").value(7));
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("DELETE /api/categories/{id} as ADMIN should delete category and return 200")
        void deleteCategory_asAdmin_shouldReturn200() throws Exception {
                mockMvc.perform(delete("/api/categories/7").with(csrf()))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.success").value(true));
        }

        // ─── ORGANIZER denied (403) ─────────────────────────────────────────────

        @Test
        @WithMockUser(roles = "ORGANIZER")
        @DisplayName("POST /api/categories as ORGANIZER should return 403 Forbidden")
        void createCategory_asOrganizer_shouldReturn403() throws Exception {
                CreateCategoryRequest request = CreateCategoryRequest.builder()
                                .name("Rogue Category")
                                .description("Should not be created")
                                .iconUrl("skull")
                                .build();

                mockMvc.perform(post("/api/categories").with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(roles = "ORGANIZER")
        @DisplayName("DELETE /api/categories/{id} as ORGANIZER should return 403 Forbidden")
        void deleteCategory_asOrganizer_shouldReturn403() throws Exception {
                mockMvc.perform(delete("/api/categories/7").with(csrf()))
                                .andExpect(status().isForbidden());
        }

        // ─── Public GET endpoints ───────────────────────────────────────────────

        @Test
        @DisplayName("GET /api/categories is public and should return 200 without authentication")
        void listCategories_withoutAuth_shouldReturn200() throws Exception {
                when(categoryService.listCategories(any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

                mockMvc.perform(get("/api/categories"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.success").value(true));
        }
}
