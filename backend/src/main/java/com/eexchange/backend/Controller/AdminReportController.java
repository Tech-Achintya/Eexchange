package com.eexchange.backend.Controller;

import com.eexchange.backend.Configuration.CustomAnnotations.CurrentAdmin;
import com.eexchange.backend.DTO.AdminPrincipal;
import com.eexchange.backend.DTO.Request.AdminReportActionRequest;
import com.eexchange.backend.DTO.Response.AdminReportResponse;
import com.eexchange.backend.DTO.Response.ApiResponse;
import com.eexchange.backend.DTO.Response.PageResponse;
import com.eexchange.backend.Entity.Enums.ReportStatus;
import com.eexchange.backend.Exception.AppException;
import com.eexchange.backend.Service.AdminReportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/reports")
@RequiredArgsConstructor
public class AdminReportController {

    private final AdminReportService adminReportService;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<AdminReportResponse>>> list(
            @CurrentAdmin AdminPrincipal principal,
            @RequestParam(defaultValue = "OPEN") ReportStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        requireAdmin(principal);

        return ResponseEntity.ok(ApiResponse.ok("Reports loaded",
                adminReportService.list(status, page, size)));
    }

    @PatchMapping("/{id:\\d+}")
    public ResponseEntity<ApiResponse<Void>> act(
            @CurrentAdmin AdminPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody AdminReportActionRequest request) {

        AdminPrincipal admin = requireAdmin(principal);
        adminReportService.act(admin.adminId(), id, request);

        return ResponseEntity.ok(ApiResponse.ok("Report updated"));
    }

    private AdminPrincipal requireAdmin(AdminPrincipal principal) {
        if (principal == null) {
            throw new AppException("Admin authentication required", HttpStatus.UNAUTHORIZED);
        }
        return principal;
    }
}