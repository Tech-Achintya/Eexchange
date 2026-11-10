package com.eexchange.backend.Controller;

import com.eexchange.backend.DTO.Request.AdminLoginRequest;
import com.eexchange.backend.DTO.Response.ApiResponse;
import com.eexchange.backend.Entity.Admin;
import com.eexchange.backend.Exception.AppException;
import com.eexchange.backend.Repository.AdminRepository;
import com.eexchange.backend.Service.LoginLockoutService;
import com.eexchange.backend.Utils.JWTutil;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/auth")
public class AdminAuthController {

    private final AdminRepository adminRepository;
    private final PasswordEncoder passwordEncoder;
    private final JWTutil jwtUtil;
    private final LoginLockoutService loginLockoutService;
    private final String dummyPasswordHash;

    public AdminAuthController(AdminRepository adminRepository,
                               PasswordEncoder passwordEncoder,
                               JWTutil jwtUtil,
                               LoginLockoutService loginLockoutService) {
        this.adminRepository = adminRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.loginLockoutService = loginLockoutService;
        this.dummyPasswordHash = passwordEncoder.encode(
                "dummy-admin-password-" + UUID.randomUUID()
        );
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<Map<String, Object>>> adminLogin(
            @Valid @RequestBody AdminLoginRequest request,
            jakarta.servlet.http.HttpServletRequest httpRequest) {

        String identifier = request.getUsernameOrEmail().trim();
        String clientIp = httpRequest.getRemoteAddr();

        Admin admin = adminRepository.findByEmail(identifier.toLowerCase())
                .or(() -> adminRepository.findByUsername(identifier))
                .orElse(null);

        // Always run BCrypt, even for unknown admins.
        String storedHash = admin != null ? admin.getPasswordHash() : dummyPasswordHash;
        boolean passwordMatches = passwordEncoder.matches(request.getPassword(), storedHash);

        // Do not reveal account state unless the password is correct. The
        // password is checked before the lockout so a correct password from the
        // real admin always works, even if another address is guessing.
        if (admin == null || !passwordMatches) {
            loginLockoutService.recordFailure(
                    LoginLockoutService.SCOPE_ADMIN, identifier, clientIp);
            loginLockoutService.checkNotLocked(
                    LoginLockoutService.SCOPE_ADMIN, identifier, clientIp);
            throw new AppException("Invalid admin credentials", HttpStatus.UNAUTHORIZED);
        }

        if (!Boolean.TRUE.equals(admin.getIsActive())) {
            throw new AppException("Admin account is deactivated.", HttpStatus.FORBIDDEN);
        }

        loginLockoutService.clearFailures(LoginLockoutService.SCOPE_ADMIN, identifier);

        admin.setLastLoginAt(Instant.now());
        adminRepository.save(admin);

        String role = "ROLE_" + admin.getRole().name();
        String token = jwtUtil.generateAdminToken(admin.getUsername(), admin.getId(), role);

        return ResponseEntity.ok(ApiResponse.ok("Admin authenticated successfully", Map.of(
                "token", token,
                "adminId", admin.getId(),
                "username", admin.getUsername(),
                "role", admin.getRole().name()
        )));
    }
}