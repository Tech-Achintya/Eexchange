package com.eexchange.backend.Controller;

import com.eexchange.backend.Configuration.CurrentUserArgumentResolver;
import com.eexchange.backend.DTO.Response.PageResponse;
import com.eexchange.backend.DTO.UserPrincipal;
import com.eexchange.backend.Exception.GlobalExceptionHandler;
import com.eexchange.backend.Service.InterestService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class InterestControllerMockMvcTest {

    private InterestService interestService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        interestService = mock(InterestService.class);

        mockMvc = MockMvcBuilders
                .standaloneSetup(new InterestController(interestService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new CurrentUserArgumentResolver())
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
    void validRequestReturns201() throws Exception {
        mockMvc.perform(post("/api/posts/1/interest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message":"Is this still available?","contactInfo":"9876543210"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true));

        verify(interestService).create(eq(7L), eq(1L), any());
    }

    @Test
    void invalidPayloadReturns400() throws Exception {
        mockMvc.perform(post("/api/posts/1/interest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message":"hi","contactInfo":"9876543210"}
                                """))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/posts/1/interest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message":"Is this still available?"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void receivedRouteForwardsFilters() throws Exception {
        when(interestService.received(7L, true, 1, 5))
                .thenReturn(new PageResponse<>(List.of(), 1, 5, 0, 0));

        mockMvc.perform(get("/api/interests/received")
                        .param("unreadOnly", "true")
                        .param("page", "1")
                        .param("size", "5"))
                .andExpect(status().isOk());

        verify(interestService).received(7L, true, 1, 5);
    }

    @Test
    void sentRouteForwardsPaging() throws Exception {
        when(interestService.sent(7L, 0, 10))
                .thenReturn(new PageResponse<>(List.of(), 0, 10, 0, 0));

        mockMvc.perform(get("/api/interests/sent")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk());

        verify(interestService).sent(7L, 0, 10);
    }

    @Test
    void markReadRouteMapsToService() throws Exception {
        mockMvc.perform(patch("/api/interests/5/read"))
                .andExpect(status().isOk());

        verify(interestService).markRead(7L, 5L);
    }

    @Test
    void unreadCountRouteReturnsCount() throws Exception {
        when(interestService.unreadCount(7L)).thenReturn(3L);

        mockMvc.perform(get("/api/interests/unread-count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.count").value(3));
    }
}