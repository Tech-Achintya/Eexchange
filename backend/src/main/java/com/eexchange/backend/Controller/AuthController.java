package com.eexchange.backend.Controller;

import com.eexchange.backend.DTO.*;
import com.eexchange.backend.Exception.AppException;
import com.eexchange.backend.Service.AuthService;
import com.eexchange.backend.Service.CollegeDomainService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping({"/api/auth", "/auth"})
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<Map<String, Object>>> signup(@Valid @RequestBody SignupRequest request) {
        CollegeDomainService.CollegeMatchResult matchResult = authService.signup(request);

        Map<String, Object> data = Map.of(
                "collegeName", matchResult.getCollege().getName(),
                "isCollegeVerified", matchResult.isCollegeVerified()
        );

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Verification OTP sent to your email. Please verify to complete registration.", data));
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<ApiResponse<AuthResponse>> verifyOtp(@Valid @RequestBody VerifyOtpRequest request) {
        AuthResponse response = authService.verifyOtp(request);
        return ResponseEntity.ok(ApiResponse.ok("Registration verified successfully", response));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(ApiResponse.ok("Login successful", response));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse<Void>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request);
        return ResponseEntity.ok(ApiResponse.ok("Password reset OTP sent to your email."));
    }

    @PostMapping("/resend-otp")
    public ResponseEntity<ApiResponse<Void>> resendOtp(@Valid @RequestBody ResendOtpRequest request) {
        authService.resendOtp(request);
        return ResponseEntity.ok(ApiResponse.ok("A new OTP has been sent to your email."));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<Void>> resetPassword(
            @RequestParam(required = false) String token,
            @Valid @RequestBody ResetPasswordRequest request) {

        if (token != null && !token.isBlank()) {
            request.setToken(token);
        }
        if (request.getToken() == null || request.getToken().isBlank()) {
            throw new AppException("Reset token is required", HttpStatus.BAD_REQUEST);
        }

        authService.resetPassword(request);
        return ResponseEntity.ok(ApiResponse.ok("Password has been reset successfully. You can now log in with your new password."));
    }


    @GetMapping("/validate-reset-token")
    public ResponseEntity<ApiResponse<Boolean>> validateResetToken(@RequestParam String token) {
        boolean isValid = authService.validateResetToken(token);
        if (!isValid) {
            throw new AppException("Reset link has expired or is invalid.", HttpStatus.BAD_REQUEST);
        }
        return ResponseEntity.ok(ApiResponse.ok("Token is valid", true));
    }
}
