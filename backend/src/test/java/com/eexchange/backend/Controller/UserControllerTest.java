package com.eexchange.backend.Controller;

import com.eexchange.backend.DTO.UpdateProfileRequest;
import com.eexchange.backend.DTO.UserPrincipal;
import com.eexchange.backend.DTO.UserProfileResponse;
import com.eexchange.backend.Entity.Enums.UserStatus;
import com.eexchange.backend.Exception.AppException;
import com.eexchange.backend.Exception.GlobalExceptionHandler;
import com.eexchange.backend.Service.UserProfileService;
import com.eexchange.backend.Configuration.CurrentUserArgumentResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserControllerTest {

    private UserProfileService userProfileService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        userProfileService = mock(UserProfileService.class);

        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

        mockMvc = MockMvcBuilders
                .standaloneSetup(new UserController(userProfileService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new CurrentUserArgumentResolver())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(mapper))
                .build();

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        new UserPrincipal(7L, "user@college.edu", 3L),
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void blankNameReturns400() throws Exception {
        mockMvc.perform(patch("/api/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"   \"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(userProfileService);
    }

    @Test
    void tooShortNameReturns400() throws Exception {
        mockMvc.perform(patch("/api/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"a\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(userProfileService);
    }

    @Test
    void tooLongNameReturns400() throws Exception {
        String tooLong = "a".repeat(101);

        mockMvc.perform(patch("/api/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + tooLong + "\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(userProfileService);
    }

    @Test
    void validNameReturnsUpdatedProfile() throws Exception {
        UserProfileResponse response = new UserProfileResponse(
                7L,
                "user@college.edu",
                "New Name",
                new UserProfileResponse.CollegeSummary(3L, "Test College"),
                true,
                UserStatus.ACTIVE,
                Instant.parse("2026-01-01T00:00:00Z"));

        when(userProfileService.updateProfile(eq(7L), any(UpdateProfileRequest.class)))
                .thenReturn(response);

        mockMvc.perform(patch("/api/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"  New Name  \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("New Name"));
    }

    @Test
    void wrongOldPasswordReturns400() throws Exception {
        when(userProfileService.changePassword(eq(7L), any()))
                .thenThrow(new AppException("Current password is incorrect",
                        HttpStatus.BAD_REQUEST));

        mockMvc.perform(post("/api/users/me/change-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"oldPassword\":\"wrong\",\"newPassword\":\"newpass123\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void weakNewPasswordReturns400() throws Exception {
        mockMvc.perform(post("/api/users/me/change-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"oldPassword\":\"oldpass123\",\"newPassword\":\"short\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(userProfileService);
    }

    @Test
    void wrongDeletePasswordReturns400() throws Exception {
        doThrow(new AppException(
                "Password is incorrect",
                HttpStatus.BAD_REQUEST
        )).when(userProfileService).deleteAccount(eq(7L), any());

        mockMvc.perform(delete("/api/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"wrong\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deleteAccountReturns200() throws Exception {
        mockMvc.perform(delete("/api/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"oldpass123\"}"))
                .andExpect(status().isOk());

        verify(userProfileService).deleteAccount(eq(7L), any());
    }
}