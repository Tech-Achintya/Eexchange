package com.eexchange.backend.Controller;

import com.eexchange.backend.Configuration.CustomAnnotations.CurrentAdmin;
import com.eexchange.backend.Configuration.CustomAnnotations.RateLimit;
import com.eexchange.backend.DTO.AdminPrincipal;
import com.eexchange.backend.DTO.Response.ApiResponse;
import com.eexchange.backend.DTO.Response.AuditLogResponse;
import com.eexchange.backend.DTO.Response.PageResponse;
import com.eexchange.backend.Entity.Enums.AdminRole;
import com.eexchange.backend.Entity.Enums.AuditAction;
import com.eexchange.backend.Entity.Enums.AuditTargetType;
import com.eexchange.backend.Exception.AppException;
import com.eexchange.backend.Service.AdminAuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;

@RestController
@RequestMapping("/api/admin/audit-log")
@RequiredArgsConstructor
public class AdminAuditController {

    private final AdminAuditService adminAuditService;

    @GetMapping
    @RateLimit(name = "admin-audit-log", limit = 120, windowSeconds = 60,
            scope = RateLimit.Scope.USER)
    public ResponseEntity<ApiResponse<PageResponse<AuditLogResponse>>> list(
            @CurrentAdmin AdminPrincipal principal,
            @RequestParam(required = false) AuditTargetType targetType,
            @RequestParam(required = false) Long targetId,
            @RequestParam(required = false) Long actorId,
            @RequestParam(required = false) AuditAction action,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        if (principal == null) {
            throw new AppException("Admin authentication required", HttpStatus.UNAUTHORIZED);
        }
        // Defence in depth: SecurityConfig also restricts /api/admin/** by role.
        if (principal.role() != AdminRole.SUPER_ADMIN) {
            throw new AppException("Only super admins can read the audit log",
                    HttpStatus.FORBIDDEN);
        }

        return ResponseEntity.ok(ApiResponse.ok("Audit log loaded",
                adminAuditService.list(targetType, targetId, actorId, action,
                        from == null ? null : from.toInstant(),
                        to == null ? null : to.toInstant(),
                        page, size)));
    }
}
