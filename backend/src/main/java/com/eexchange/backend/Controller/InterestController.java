package com.eexchange.backend.Controller;

import com.eexchange.backend.Configuration.CustomAnnotations.CurrentUser;
import com.eexchange.backend.Configuration.CustomAnnotations.RateLimit;
import com.eexchange.backend.DTO.Request.InterestCreateRequest;
import com.eexchange.backend.DTO.Response.ApiResponse;
import com.eexchange.backend.DTO.Response.InterestReceivedResponse;
import com.eexchange.backend.DTO.Response.InterestSentResponse;
import com.eexchange.backend.DTO.Response.PageResponse;
import com.eexchange.backend.DTO.Response.UnreadCountResponse;
import com.eexchange.backend.DTO.UserPrincipal;
import com.eexchange.backend.Exception.AppException;
import com.eexchange.backend.Service.InterestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class InterestController {

    private final InterestService interestService;

    @PostMapping("/api/posts/{id:\\d+}/interest")
    @RateLimit(name = "interest-create-minute", limit = 5, windowSeconds = 60,
            scope = RateLimit.Scope.USER)
    @RateLimit(name = "interest-create-day", limit = 30, windowSeconds = 86400,
            scope = RateLimit.Scope.USER)
    public ResponseEntity<ApiResponse<Void>> send(
            @CurrentUser UserPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody InterestCreateRequest request) {

        interestService.create(requireUserId(principal), id, request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Interest sent"));
    }

    @GetMapping("/api/interests/received")
    public ResponseEntity<ApiResponse<PageResponse<InterestReceivedResponse>>> received(
            @CurrentUser UserPrincipal principal,
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        return ResponseEntity.ok(ApiResponse.ok("Received interests",
                interestService.received(requireUserId(principal), unreadOnly, page, size)));
    }

    @GetMapping("/api/interests/sent")
    public ResponseEntity<ApiResponse<PageResponse<InterestSentResponse>>> sent(
            @CurrentUser UserPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        return ResponseEntity.ok(ApiResponse.ok("Sent interests",
                interestService.sent(requireUserId(principal), page, size)));
    }

    @PatchMapping("/api/interests/{id:\\d+}/read")
    public ResponseEntity<ApiResponse<Void>> markRead(
            @CurrentUser UserPrincipal principal,
            @PathVariable Long id) {

        interestService.markRead(requireUserId(principal), id);

        return ResponseEntity.ok(ApiResponse.ok("Marked as read"));
    }

    @GetMapping("/api/interests/unread-count")
    public ResponseEntity<ApiResponse<UnreadCountResponse>> unreadCount(
            @CurrentUser UserPrincipal principal) {

        long count = interestService.unreadCount(requireUserId(principal));

        return ResponseEntity.ok(ApiResponse.ok("Unread count",
                new UnreadCountResponse(count)));
    }

    private Long requireUserId(UserPrincipal principal) {
        if (principal == null) {
            throw new AppException("Authentication required", HttpStatus.UNAUTHORIZED);
        }
        return principal.userId();
    }
}
