package com.eexchange.backend.Controller;

import com.eexchange.backend.Configuration.CustomAnnotations.CurrentUser;
import com.eexchange.backend.Configuration.CustomAnnotations.RateLimit;
import com.eexchange.backend.DTO.Response.ApiResponse;
import com.eexchange.backend.DTO.Request.PostCreateRequest;
import com.eexchange.backend.DTO.Response.PostResponse;
import com.eexchange.backend.DTO.UserPrincipal;
import com.eexchange.backend.Exception.AppException;
import com.eexchange.backend.Service.PostService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/posts")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;

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
}