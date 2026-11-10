package com.eexchange.backend.Controller;

import com.eexchange.backend.Configuration.CustomAnnotations.CurrentAdmin;
import com.eexchange.backend.DTO.AdminPrincipal;
import com.eexchange.backend.DTO.Request.AdminUserBlockRequest;
import com.eexchange.backend.DTO.Request.AdminUserUnblockRequest;
import com.eexchange.backend.DTO.Response.AdminUserDetailResponse;
import com.eexchange.backend.DTO.Response.AdminUserRowResponse;
import com.eexchange.backend.DTO.Response.ApiResponse;
import com.eexchange.backend.DTO.Response.PageResponse;
import com.eexchange.backend.Entity.Enums.UserStatus;
import com.eexchange.backend.Exception.AppException;
import com.eexchange.backend.Service.AdminUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserService adminUserService;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<AdminUserRowResponse>>> list(
            @CurrentAdmin AdminPrincipal principal,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) UserStatus status,
            @RequestParam(required = false) Long collegeId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        requireAdmin(principal);

        return ResponseEntity.ok(ApiResponse.ok("Users loaded",
                adminUserService.list(q, status, collegeId, page, size)));
    }

    @GetMapping("/{id:\\d+}")
    public ResponseEntity<ApiResponse<AdminUserDetailResponse>> detail(
            @CurrentAdmin AdminPrincipal principal,
            @PathVariable Long id) {

        requireAdmin(principal);

        return ResponseEntity.ok(ApiResponse.ok("User loaded",
                adminUserService.detail(id)));
    }

    @PostMapping("/{id:\\d+}/block")
    public ResponseEntity<ApiResponse<Void>> block(
            @CurrentAdmin AdminPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody AdminUserBlockRequest request) {

        AdminPrincipal admin = requireAdmin(principal);
        adminUserService.block(admin.adminId(), admin.role(), id, request);

        return ResponseEntity.ok(ApiResponse.ok("User blocked"));
    }

    @PostMapping("/{id:\\d+}/unblock")
    public ResponseEntity<ApiResponse<Void>> unblock(
            @CurrentAdmin AdminPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody AdminUserUnblockRequest  request) {

        AdminPrincipal admin = requireAdmin(principal);
        adminUserService.unblock(admin.adminId(), admin.role(), id, request);

        return ResponseEntity.ok(ApiResponse.ok("User unblocked"));
    }

    private AdminPrincipal requireAdmin(AdminPrincipal principal) {
        if (principal == null) {
            throw new AppException("Admin authentication required", HttpStatus.UNAUTHORIZED);
        }
        return principal;
    }
}