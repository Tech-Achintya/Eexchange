package com.eexchange.backend.Controller;

import com.eexchange.backend.Configuration.CustomAnnotations.CurrentAdmin;
import com.eexchange.backend.DTO.AdminPrincipal;
import com.eexchange.backend.DTO.Request.AdminModerationApproveRequest;
import com.eexchange.backend.DTO.Request.AdminModerationRejectRequest;
import com.eexchange.backend.DTO.Request.AdminModerationRequeueRequest;
import com.eexchange.backend.DTO.Response.AdminModerationQueueItemResponse;
import com.eexchange.backend.DTO.Response.ApiResponse;
import com.eexchange.backend.DTO.Response.PageResponse;
import com.eexchange.backend.Exception.AppException;
import com.eexchange.backend.Service.AdminModerationService;
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
@RequestMapping("/api/admin/moderation")
@RequiredArgsConstructor
public class AdminModerationController {

    private final AdminModerationService adminModerationService;

    @GetMapping("/queue")
    public ResponseEntity<ApiResponse<PageResponse<AdminModerationQueueItemResponse>>> queue(
            @CurrentAdmin AdminPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        requireAdmin(principal);

        return ResponseEntity.ok(ApiResponse.ok("Manual review queue loaded",
                adminModerationService.queue(page, size)));
    }

    @PostMapping("/{postId:\\d+}/approve")
    public ResponseEntity<ApiResponse<Void>> approve(
            @CurrentAdmin AdminPrincipal principal,
            @PathVariable Long postId,
            @Valid @RequestBody(required = false) AdminModerationApproveRequest request) {

        AdminPrincipal admin = requireAdmin(principal);
        adminModerationService.approve(admin.adminId(), postId,
                request == null ? null : request.getNote());

        return ResponseEntity.ok(ApiResponse.ok("Post approved"));
    }

    @PostMapping("/{postId:\\d+}/reject")
    public ResponseEntity<ApiResponse<Void>> reject(
            @CurrentAdmin AdminPrincipal principal,
            @PathVariable Long postId,
            @Valid @RequestBody AdminModerationRejectRequest request) {

        AdminPrincipal admin = requireAdmin(principal);
        adminModerationService.reject(admin.adminId(), postId, request.getReason());

        return ResponseEntity.ok(ApiResponse.ok("Post rejected"));
    }

    @PostMapping("/{postId:\\d+}/requeue")
    public ResponseEntity<ApiResponse<Void>> requeue(
            @CurrentAdmin AdminPrincipal principal,
            @PathVariable Long postId,
            @Valid @RequestBody(required = false) AdminModerationRequeueRequest request) {

        AdminPrincipal admin = requireAdmin(principal);
        adminModerationService.requeue(admin.adminId(), postId,
                request == null ? null : request.getNote());

        return ResponseEntity.ok(ApiResponse.ok("Post requeued for AI review"));
    }

    private AdminPrincipal requireAdmin(AdminPrincipal principal) {
        if (principal == null) {
            throw new AppException("Admin authentication required", HttpStatus.UNAUTHORIZED);
        }
        return principal;
    }
}
