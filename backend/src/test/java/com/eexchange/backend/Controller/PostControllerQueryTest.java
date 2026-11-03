package com.eexchange.backend.Controller;

import com.eexchange.backend.Configuration.CurrentUserArgumentResolver;
import com.eexchange.backend.DTO.Response.PageResponse;
import com.eexchange.backend.DTO.Response.PostCardResponse;
import com.eexchange.backend.DTO.Response.PostDetailResponse;
import com.eexchange.backend.DTO.UserPrincipal;
import com.eexchange.backend.Entity.Enums.ItemCondition;
import com.eexchange.backend.Entity.Enums.ListingType;
import com.eexchange.backend.Entity.Enums.PostStatus;
import com.eexchange.backend.Exception.GlobalExceptionHandler;
import com.eexchange.backend.Service.PostQueryService;
import com.eexchange.backend.Service.PostService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PostControllerQueryTest {

    private PostService postService;
    private PostQueryService postQueryService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        postService = mock(PostService.class);
        postQueryService = mock(PostQueryService.class);

        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

        mockMvc = MockMvcBuilders
                .standaloneSetup(new PostController(postQueryService,postService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new CurrentUserArgumentResolver())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(mapper))
                .build();

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        new UserPrincipal(7L, "user@college.edu", 10L),
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void feedUsesDefaultPagingAndMapsCards() throws Exception {
        when(postQueryService.feed(eq(7L), isNull(), isNull(), isNull(),
                isNull(), isNull(), eq("newest"), eq(0), eq(12)))
                .thenReturn(new PageResponse<>(List.of(card()), 0, 12, 1, 1));

        mockMvc.perform(get("/api/posts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].title").value("Post 1"))
                .andExpect(jsonPath("$.data.content[0].firstImageUrl")
                        .value("https://cdn.test/a.jpg"))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.page").value(0));
    }

    @Test
    void feedForwardsEveryQueryParameter() throws Exception {
        when(postQueryService.feed(any(), any(), any(), any(), any(), any(),
                any(), anyInt(), anyInt()))
                .thenReturn(new PageResponse<>(List.of(), 1, 5, 0, 0));

        mockMvc.perform(get("/api/posts")
                        .param("category", "3")
                        .param("type", "SELL")
                        .param("q", "lamp")
                        .param("minPrice", "10")
                        .param("maxPrice", "100")
                        .param("sort", "priceAsc")
                        .param("page", "1")
                        .param("size", "5"))
                .andExpect(status().isOk());

        verify(postQueryService).feed(eq(7L), eq(3L), eq(ListingType.SELL),
                eq("lamp"), eq(new BigDecimal("10")), eq(new BigDecimal("100")),
                eq("priceAsc"), eq(1), eq(5));
    }

    @Test
    void numericPathMapsToDetail() throws Exception {
        when(postQueryService.detail(7L, 5L)).thenReturn(detail());

        mockMvc.perform(get("/api/posts/5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(5))
                .andExpect(jsonPath("$.data.imageUrls[0]")
                        .value("https://cdn.test/a.jpg"));

        verify(postQueryService).detail(7L, 5L);
    }

    @Test
    void mineIsNotCapturedByTheIdRoute() throws Exception {
        when(postQueryService.mine(eq(7L), eq(PostStatus.REJECTED), eq(0), eq(12)))
                .thenReturn(new PageResponse<>(List.of(), 0, 12, 0, 0));

        mockMvc.perform(get("/api/posts/mine").param("status", "REJECTED"))
                .andExpect(status().isOk());

        verify(postQueryService).mine(7L, PostStatus.REJECTED, 0, 12);
    }

        @Test
        void nonNumericIdIsNotRoutedToDetail() throws Exception {
            mockMvc.perform(get("/api/posts/abc"))
                    .andExpect(status().isNotFound());

        verifyNoInteractions(postQueryService);
    }

    @Test
    void detailWithoutAuthenticationReturns401() throws Exception {
        SecurityContextHolder.clearContext();

        mockMvc.perform(get("/api/posts/5"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(postQueryService);
    }

    // ---------------------------------------------------------- fixtures

    private PostCardResponse card() {
        return new PostCardResponse(1L, "Post 1", new BigDecimal("100.00"),
                ListingType.SELL, ItemCondition.GOOD, "https://cdn.test/a.jpg",
                "Books", "Alice", Instant.now());
    }

    private PostDetailResponse detail() {
        return new PostDetailResponse(5L, "Post 5", "A long enough description",
                new BigDecimal("100.00"), ListingType.SELL, null, ItemCondition.GOOD,
                5L, "Books", PostStatus.APPROVED, null, 10L, 7L, "Alice",
                List.of("https://cdn.test/a.jpg"), Instant.now(), Instant.now());
    }
}