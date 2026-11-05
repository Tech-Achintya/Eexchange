package com.eexchange.backend.Controller;

import com.eexchange.backend.Configuration.CustomAnnotations.CurrentUser;
import com.eexchange.backend.Configuration.CustomAnnotations.RateLimit;
import com.eexchange.backend.DTO.Request.PostCreateRequest;
import com.eexchange.backend.DTO.Request.PostUpdateRequest;
import com.eexchange.backend.DTO.Response.ApiResponse;
import com.eexchange.backend.DTO.Response.PageResponse;
import com.eexchange.backend.DTO.Response.PostCardResponse;
import com.eexchange.backend.DTO.Response.PostDetailResponse;
import com.eexchange.backend.DTO.Response.PostResponse;
import com.eexchange.backend.DTO.UserPrincipal;
import com.eexchange.backend.Entity.Enums.PostStatus;
import com.eexchange.backend.Exception.AppException;
import com.eexchange.backend.Service.PostQueryService;
import com.eexchange.backend.Service.PostService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/posts")
@RequiredArgsConstructor
public class PostController {

    private final PostQueryService postQueryService;
    private final PostService postService;

    // ------------------------------------------------------------- create

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RateLimit(name = "posts-create-minute", limit = 3, windowSeconds = 60,
            scope = RateLimit.Scope.USER)
    @RateLimit(name = "posts-create-day", limit = 10, windowSeconds = 86400,
            scope = RateLimit.Scope.USER)
    public ResponseEntity<ApiResponse<PostResponse>> createPost(
            @CurrentUser UserPrincipal principal,
            @Valid @RequestPart("post") PostCreateRequest request,
            @RequestPart("images") List<MultipartFile> images) {

        PostResponse response = postService.createPost(
                requireUserId(principal), request, images);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Post created and submitted for review", response));
    }

    // ------------------------------------------------------------- update

    @PatchMapping(value = "/{id:\\d+}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RateLimit(name = "posts-update", limit = 20, windowSeconds = 60,
            scope = RateLimit.Scope.USER)
    public ResponseEntity<ApiResponse<PostResponse>> updatePost(
            @CurrentUser UserPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestPart("post") PostUpdateRequest request,
            @RequestPart(value = "newImages", required = false) List<MultipartFile> newImages) {

        PostResponse response = postService.updatePost(
                requireUserId(principal), id, request, newImages);

        return ResponseEntity.ok(
                ApiResponse.ok("Post updated and submitted for review", response));
    }

    // --------------------------------------------------------------- sold

    @PostMapping("/{id:\\d+}/sold")
    @RateLimit(name = "posts-sold", limit = 20, windowSeconds = 60,
            scope = RateLimit.Scope.USER)
    public ResponseEntity<ApiResponse<PostResponse>> markSold(
            @CurrentUser UserPrincipal principal,
            @PathVariable Long id) {

        return ResponseEntity.ok(ApiResponse.ok("Post marked as sold",
                postService.markSold(requireUserId(principal), id)));
    }

    // ------------------------------------------------------------- delete

    @DeleteMapping("/{id:\\d+}")
    @RateLimit(name = "posts-delete", limit = 20, windowSeconds = 60,
            scope = RateLimit.Scope.USER)
    public ResponseEntity<Void> deletePost(
            @CurrentUser UserPrincipal principal,
            @PathVariable Long id) {

        postService.deletePost(requireUserId(principal), id);
        return ResponseEntity.noContent().build();
    }

    // --------------------------------------------------------------- read

    @GetMapping
    @RateLimit(name = "posts-feed", limit = 60, windowSeconds = 60,
            scope = RateLimit.Scope.USER)
    public ResponseEntity<ApiResponse<PageResponse<PostCardResponse>>> feed(
            @CurrentUser UserPrincipal principal,
            @RequestParam(required = false) Long category,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(defaultValue = "newest") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size) {

        PageResponse<PostCardResponse> response = postQueryService.feed(
                requireUserId(principal), category, q,
                minPrice, maxPrice, sort, page, size);

        return ResponseEntity.ok(ApiResponse.ok("Feed loaded", response));
    }

    // \d+ keeps "/mine" from being captured as an id.
    @GetMapping("/{id:\\d+}")
    @RateLimit(name = "posts-detail", limit = 60, windowSeconds = 60,
            scope = RateLimit.Scope.USER)
    public ResponseEntity<ApiResponse<PostDetailResponse>> detail(
            @CurrentUser UserPrincipal principal,
            @PathVariable Long id) {

        return ResponseEntity.ok(ApiResponse.ok(
                "Post loaded", postQueryService.detail(requireUserId(principal), id)));
    }

    @GetMapping("/mine")
    @RateLimit(name = "posts-mine", limit = 30, windowSeconds = 60,
            scope = RateLimit.Scope.USER)
    public ResponseEntity<ApiResponse<PageResponse<PostDetailResponse>>> mine(
            @CurrentUser UserPrincipal principal,
            @RequestParam(required = false) PostStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size) {

        return ResponseEntity.ok(ApiResponse.ok("Your posts",
                postQueryService.mine(requireUserId(principal), status, page, size)));
    }

    private Long requireUserId(UserPrincipal principal) {
        if (principal == null) {
            throw new AppException("Authentication required", HttpStatus.UNAUTHORIZED);
        }
        return principal.userId();
    }
}