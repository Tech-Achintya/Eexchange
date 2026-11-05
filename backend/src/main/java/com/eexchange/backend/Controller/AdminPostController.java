package com.eexchange.backend.Controller;

import com.eexchange.backend.Configuration.CustomAnnotations.CurrentAdmin;
import com.eexchange.backend.Configuration.CustomAnnotations.RateLimit;
import com.eexchange.backend.DTO.AdminPrincipal;
import com.eexchange.backend.DTO.Request.AdminPostDeleteRequest;
import com.eexchange.backend.DTO.Response.AdminPostCardResponse;
import com.eexchange.backend.DTO.Response.AdminPostDetailResponse;
import com.eexchange.backend.DTO.Response.ApiResponse;
import com.eexchange.backend.DTO.Response.PageResponse;
import com.eexchange.backend.Entity.Enums.PostStatus;
import com.eexchange.backend.Exception.AppException;
import com.eexchange.backend.Service.AdminPostService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/posts")
@RequiredArgsConstructor
public class AdminPostController {

    private final AdminPostService adminPostService;

    @GetMapping
    @RateLimit(name = "admin-posts-list", limit = 120, windowSeconds = 60,
            scope = RateLimit.Scope.USER)
    public ResponseEntity<ApiResponse<PageResponse<AdminPostCardResponse>>> list(
            @CurrentAdmin AdminPrincipal principal,
            @RequestParam(required = false) PostStatus status,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Long collegeId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        requireAdmin(principal);

        return ResponseEntity.ok(ApiResponse.ok("Posts loaded",
                adminPostService.list(status, q, collegeId, page, size)));
    }

    @GetMapping("/{id:\\d+}")
    @RateLimit(name = "admin-posts-detail", limit = 120, windowSeconds = 60,
            scope = RateLimit.Scope.USER)
    public ResponseEntity<ApiResponse<AdminPostDetailResponse>> detail(
            @CurrentAdmin AdminPrincipal principal,
            @PathVariable Long id) {

        requireAdmin(principal);

        return ResponseEntity.ok(ApiResponse.ok("Post loaded",
                adminPostService.detail(id)));
    }

    @DeleteMapping("/{id:\\d+}")
    @RateLimit(name = "admin-posts-delete", limit = 120, windowSeconds = 60,
            scope = RateLimit.Scope.USER)
    public ResponseEntity<ApiResponse<Void>> delete(
            @CurrentAdmin AdminPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody AdminPostDeleteRequest request) {

        AdminPrincipal admin = requireAdmin(principal);
        adminPostService.delete(admin.adminId(), id, request.getReason());

        return ResponseEntity.ok(ApiResponse.ok("Post deleted"));
    }

    private AdminPrincipal requireAdmin(AdminPrincipal principal) {
        if (principal == null) {
            throw new AppException("Admin authentication required", HttpStatus.UNAUTHORIZED);
        }
        return principal;
    }
}