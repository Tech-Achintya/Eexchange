package com.eexchange.backend.Controller;

import com.eexchange.backend.DTO.ApiResponse;
import com.eexchange.backend.DTO.ChangePasswordRequest;
import com.eexchange.backend.DTO.DeleteAccountRequest;
import com.eexchange.backend.DTO.TokenResponse;
import com.eexchange.backend.DTO.UpdateProfileRequest;
import com.eexchange.backend.DTO.UserPrincipal;
import com.eexchange.backend.DTO.UserProfileResponse;
import com.eexchange.backend.Exception.AppException;
import com.eexchange.backend.Service.UserProfileService;
import com.eexchange.backend.Configuration.CustomAnnotations.CurrentUser;
import com.eexchange.backend.Configuration.CustomAnnotations.RateLimit;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserProfileService userProfileService;

    public UserController(UserProfileService userProfileService) {
        this.userProfileService = userProfileService;
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getMe(
            @CurrentUser UserPrincipal principal) {

        return ResponseEntity.ok(ApiResponse.ok(
                "Profile retrieved",
                userProfileService.getProfile(requireUserId(principal))));
    }

    @PatchMapping("/me")
    @RateLimit(name = "users-update-profile", limit = 20, windowSeconds = 60,
            scope = RateLimit.Scope.USER)
    public ResponseEntity<ApiResponse<UserProfileResponse>> updateMe(
            @CurrentUser UserPrincipal principal,
            @Valid @RequestBody UpdateProfileRequest request) {

        return ResponseEntity.ok(ApiResponse.ok(
                "Profile updated",
                userProfileService.updateProfile(requireUserId(principal), request)));
    }

    @PostMapping("/me/change-password")
    @RateLimit(name = "users-change-password", limit = 5, windowSeconds = 60,
            scope = RateLimit.Scope.USER)
    public ResponseEntity<ApiResponse<TokenResponse>> changePassword(
            @CurrentUser UserPrincipal principal,
            @Valid @RequestBody ChangePasswordRequest request) {

        TokenResponse token = userProfileService.changePassword(
                requireUserId(principal), request);

        return ResponseEntity.ok(ApiResponse.ok(
                "Password changed successfully. Your other sessions have been signed out.",
                token));
    }

    @DeleteMapping("/me")
    @RateLimit(name = "users-delete-account", limit = 5, windowSeconds = 60,
            scope = RateLimit.Scope.USER)
    public ResponseEntity<ApiResponse<Void>> deleteMe(
            @CurrentUser UserPrincipal principal,
            @Valid @RequestBody DeleteAccountRequest request) {

        userProfileService.deleteAccount(requireUserId(principal), request);

        return ResponseEntity.ok(ApiResponse.<Void>ok("Account deleted successfully"));
    }

    private Long requireUserId(UserPrincipal principal) {
        if (principal == null) {
            throw new AppException("Authentication required", HttpStatus.UNAUTHORIZED);
        }
        return principal.userId();
    }
}