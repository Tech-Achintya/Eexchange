package com.eexchange.backend.Controller;

import com.eexchange.backend.Configuration.CustomAnnotations.CurrentUser;
import com.eexchange.backend.Configuration.CustomAnnotations.RateLimit;
import com.eexchange.backend.DTO.Response.*;
import com.eexchange.backend.DTO.Request.PostCreateRequest;
import com.eexchange.backend.DTO.UserPrincipal;
import com.eexchange.backend.Entity.Enums.ListingType;
import com.eexchange.backend.Entity.Enums.PostStatus;
import com.eexchange.backend.Entity.Post;
import com.eexchange.backend.Exception.AppException;
import com.eexchange.backend.Service.PostQueryService;
import com.eexchange.backend.Service.PostService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.object.UpdatableSqlQuery;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/posts")
//@RequiredArgsConstructor
public class PostController {
    private final PostQueryService postQueryService;
    private final PostService postService;
    PostController(PostQueryService postQueryService, PostService postService){
        this.postQueryService = postQueryService;
        this.postService = postService;
    }
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RateLimit(name = "posts-create-minute", limit = 3, windowSeconds = 60,
            scope = RateLimit.Scope.USER)
    @RateLimit(name = "posts-create-day", limit = 10, windowSeconds = 86400,
            scope = RateLimit.Scope.USER)
    public ResponseEntity<ApiResponse<PostResponse>> createPost(
            @CurrentUser UserPrincipal principal,
            @Valid @RequestPart("post") PostCreateRequest request,
            @RequestPart(value = "images", required = false) List<MultipartFile> images) {

        PostResponse response = postService.createPost(
                requireUserId(principal), request, images);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Post created and submitted for review", response));
    }

    private Long requireUserId(UserPrincipal principal) {
        if (principal == null) {
            throw new AppException("Authentication required", HttpStatus.UNAUTHORIZED);
        }
        return principal.userId();
    }
    @GetMapping
    @RateLimit(name = "posts-feed", limit = 60, windowSeconds = 60,
            scope = RateLimit.Scope.USER)
    public ResponseEntity<ApiResponse<PageResponse<PostCardResponse>>> feed(
            @CurrentUser UserPrincipal principal,
            @RequestParam(required = false) Long category,
            @RequestParam(required = false) ListingType type,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(defaultValue = "newest") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size) {

        PageResponse<PostCardResponse> response = postQueryService.feed(
                requireUserId(principal), category, type, q,
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
}