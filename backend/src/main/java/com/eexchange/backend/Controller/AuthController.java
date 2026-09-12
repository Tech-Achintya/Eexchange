package com.eexchange.backend.Controller;

import com.eexchange.backend.DTO.LoginRequest;
import com.eexchange.backend.DTO.OtpVerificationRequest;
import com.eexchange.backend.DTO.SignupRequest;
import com.eexchange.backend.Entity.User;
import com.eexchange.backend.Service.AuthService;
import com.eexchange.backend.Service.OtpService;
import com.eexchange.backend.utils.JWTutil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@CrossOrigin(origins = "https://frontend-eexchange.vercel.app/")
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;
    private final JWTutil jwtutil;
    private final OtpService otpService;

    @Autowired
    public AuthController(AuthService authService, JWTutil jwtutil, OtpService otpService) {
        this.authService = authService;
        this.jwtutil = jwtutil;
        this.otpService = otpService;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest loginRequest) {
        try {
            User user = authService.login(loginRequest);
            if (user != null) {
                String token = jwtutil.generateToken(user.getEmail());
                Map<String, Object> response = new HashMap<>();
                response.put("token", token);
                response.put("user", user);
                return new ResponseEntity<>(response, HttpStatus.OK);
            }
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Invalid login credentials"));
        } catch (RuntimeException e) {
            String msg = e.getMessage();
            if (msg != null && msg.contains("User not found")) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("message", "User not found"));
            }
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", msg != null ? msg : "Login failed"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "An unexpected error occurred"));
        }
    }

    // Step 1: Send OTP to email
    @PostMapping("/signup/initiate")
    public ResponseEntity<?> initiateSignup(@RequestBody Map<String, String> request) {
        String email = request.get("email");
        try {
            // Check if user already exists before sending OTP
            boolean exists = authService.userExists(email);
            if (exists) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(Map.of("message", "User already exists"));
            }
            otpService.generateAndSaveOtp(email);
            return ResponseEntity.ok(Map.of("message", "OTP sent to " + email));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Error sending OTP: " + e.getMessage()));
        }
    }

    // Step 2: Check OTP (intermediate step)
    @PostMapping("/signup/verify-otp")
    public ResponseEntity<?> verifyOtp(@RequestBody Map<String, String> request) {
        String email = request.get("email");
        String otpCode = request.get("otpCode");
        boolean isValid = otpService.checkOtp(email, otpCode);
        if (isValid) {
            return ResponseEntity.ok(Map.of("message", "OTP verified"));
        } else {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "Invalid or expired OTP"));
        }
    }

    // Step 3: Verify and complete (final step)
    @PostMapping("/signup/verify")
    public ResponseEntity<?> verifySignup(@RequestBody OtpVerificationRequest verifyRequest) {
        // Move name and password to the JSON body for consistency
        boolean isValid = otpService.validateOtp(verifyRequest.getEmail(), verifyRequest.getOtpCode());
        if (isValid) {
            SignupRequest signupRequest = new SignupRequest(verifyRequest.getName(), 
                                                           verifyRequest.getEmail(), 
                                                           verifyRequest.getPassword());
            User user = authService.signup(signupRequest);
            return new ResponseEntity<>(user, HttpStatus.CREATED);
        } else {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "Invalid or expired OTP"));
        }
    }
}
