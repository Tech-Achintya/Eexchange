package com.eexchange.backend.Controller;

import com.eexchange.backend.DTO.ApiResponse;
import com.eexchange.backend.DTO.UserPrincipal;
import com.eexchange.backend.Entity.Enums.PostStatus;
import com.eexchange.backend.Entity.Post;
import com.eexchange.backend.Entity.User;
import com.eexchange.backend.Exception.AppException;
import com.eexchange.backend.Repository.PostRepository;
import com.eexchange.backend.Repository.SavedPostRepository;
import com.eexchange.backend.Repository.UserRepository;
import com.eexchange.backend.configuration.CustomAnnotations.CurrentUser;
import com.eexchange.backend.configuration.CustomAnnotations.RateLimit;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/saved-posts")
public class SavedPostController {

    private final SavedPostRepository savedPostRepository;
    private final PostRepository postRepository;
    private final UserRepository userRepository;

    public SavedPostController(SavedPostRepository savedPostRepository,
                               PostRepository postRepository,
                               UserRepository userRepository) {
        this.savedPostRepository = savedPostRepository;
        this.postRepository = postRepository;
        this.userRepository = userRepository;
    }

    @PostMapping("/toggle/{postId}")
    @Transactional
    @RateLimit(name = "saved-posts-toggle", limit = 30, windowSeconds = 60,
            scope = RateLimit.Scope.USER)
    public ResponseEntity<ApiResponse<Map<String, Object>>> toggleSavePost(
            @PathVariable Long postId,
            @CurrentUser UserPrincipal principal) {

        Long userId = principal.userId();

        // Un-saving must still work if the post later became hidden or rejected.
        boolean alreadySaved = savedPostRepository.existsByUserIdAndPostId(userId, postId);
        if (alreadySaved) {
            savedPostRepository.deleteByUserIdAndPostId(userId, postId);
            return ResponseEntity.ok(
                    ApiResponse.ok("Post removed from wishlist", Map.of("saved", false)));
        }

        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new AppException("Post not found", HttpStatus.NOT_FOUND));

        if (post.getStatus() != PostStatus.APPROVED) {
            throw new AppException("Post not found", HttpStatus.NOT_FOUND);
        }

        if (principal.collegeId() == null
                || !post.getCollege().getId().equals(principal.collegeId())) {
            throw new AppException("Post not found", HttpStatus.NOT_FOUND);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException("User not found", HttpStatus.UNAUTHORIZED));

        savedPostRepository.insertIfAbsent(user.getId(), post.getId());

        return ResponseEntity.ok(
                ApiResponse.ok("Post saved to wishlist", Map.of("saved", true)));
    }

    @GetMapping
    @Transactional(readOnly = true)
    @RateLimit(name = "saved-posts-list", limit = 60, windowSeconds = 60,
            scope = RateLimit.Scope.USER)
    public ResponseEntity<ApiResponse<List<Long>>> getMySavedPostIds(
            @CurrentUser UserPrincipal principal) {

        List<Long> postIds = savedPostRepository.findPostIdsByUserId(principal.userId());
        return ResponseEntity.ok(ApiResponse.ok("Saved posts retrieved", postIds));
    }
}