package com.eexchange.backend.Controller;

import com.eexchange.backend.Configuration.CustomAnnotations.CurrentUser;
import com.eexchange.backend.Configuration.CustomAnnotations.RateLimit;
import com.eexchange.backend.DTO.Request.ReportCreateRequest;
import com.eexchange.backend.DTO.Response.ApiResponse;
import com.eexchange.backend.DTO.UserPrincipal;
import com.eexchange.backend.Exception.AppException;
import com.eexchange.backend.Service.ReportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/posts")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    @PostMapping("/{id:\\d+}/report")
    @RateLimit(name = "post-report-minute", limit = 5, windowSeconds = 60,
            scope = RateLimit.Scope.USER)
    @RateLimit(name = "post-report-day", limit = 20, windowSeconds = 86400,
            scope = RateLimit.Scope.USER)
    public ResponseEntity<ApiResponse<Void>> reportPost(
            @CurrentUser UserPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody ReportCreateRequest request) {

        if (principal == null) {
            throw new AppException("Authentication required", HttpStatus.UNAUTHORIZED);
        }

        reportService.reportPost(principal.userId(), id, request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Report submitted"));
    }
}