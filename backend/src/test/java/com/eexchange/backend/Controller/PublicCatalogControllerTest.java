package com.eexchange.backend.Controller;

import com.eexchange.backend.DTO.CategoryResponse;
import com.eexchange.backend.DTO.CollegeResponse;
import com.eexchange.backend.Exception.GlobalExceptionHandler;
import com.eexchange.backend.Service.CategoryService;
import com.eexchange.backend.Service.CollegeCatalogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PublicCatalogControllerTest {

    private CollegeCatalogService collegeCatalogService;
    private CategoryService categoryService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        collegeCatalogService = mock(CollegeCatalogService.class);
        categoryService = mock(CategoryService.class);

        mockMvc = MockMvcBuilders
                .standaloneSetup(
                        new CollegeController(collegeCatalogService),
                        new CategoryController(categoryService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void collegesRespondWithoutAToken() throws Exception {
        when(collegeCatalogService.getActiveColleges()).thenReturn(List.of(
                new CollegeResponse(1L, "Alpha College", "Pune", "Maharashtra", false)));

        mockMvc.perform(get("/api/colleges"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].name").value("Alpha College"))
                .andExpect(jsonPath("$.data[0].isOthers").value(false));
    }

    @Test
    void categoriesRespondWithoutAToken() throws Exception {
        when(categoryService.getActiveCategories()).thenReturn(List.of(
                new CategoryResponse(2L, "Books & Notes", "books-notes")));

        mockMvc.perform(get("/api/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].slug").value("books-notes"));
    }
}