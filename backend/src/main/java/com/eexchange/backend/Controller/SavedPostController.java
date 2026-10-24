package com.eexchange.backend.Controller;

import com.eexchange.backend.DTO.ApiResponse;
import com.eexchange.backend.Entity.Post;
import com.eexchange.backend.Entity.SavedPost;
import com.eexchange.backend.Entity.User;
import com.eexchange.backend.Exception.AppException;
import com.eexchange.backend.Repository.PostRepository;
import com.eexchange.backend.Repository.SavedPostRepository;
import com.eexchange.backend.Repository.UserRepository;
import com.eexchange.backend.utils.JWTutil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/saved-posts")
public class SavedPostController {

    private final SavedPostRepository savedPostRepository;
    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final JWTutil jwtUtil;

    public SavedPostController(SavedPostRepository savedPostRepository,
                               PostRepository postRepository,
                               UserRepository userRepository,
                               JWTutil jwtUtil) {
        this.savedPostRepository = savedPostRepository;
        this.postRepository = postRepository;
        this.userRepository = userRepository;
        this.jwtUtil = jwtUtil;
    }

    private Long extractUserIdFromRequest(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new AppException("Authentication token required", HttpStatus.UNAUTHORIZED);
        }
        return jwtUtil.extractUserId(authHeader.substring(7));
    }

    // Toggle save / unsave a post
    @PostMapping("/toggle/{postId}")
    @Transactional
    public ResponseEntity<ApiResponse<Map<String, Object>>> toggleSavePost(
            @PathVariable Long postId,
            HttpServletRequest request) {

        Long userId = extractUserIdFromRequest(request);

        boolean alreadySaved = savedPostRepository.existsByUserIdAndPostId(userId, postId);

        if (alreadySaved) {
            savedPostRepository.deleteByUserIdAndPostId(userId, postId);
            return ResponseEntity.ok(ApiResponse.ok("Post removed from wishlist", Map.of("saved", false)));
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException("User not found", HttpStatus.NOT_FOUND));
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new AppException("Post not found", HttpStatus.NOT_FOUND));

        SavedPost savedPost = SavedPost.builder()
                .user(user)
                .post(post)
                .build();
        savedPostRepository.save(savedPost);

        return ResponseEntity.ok(ApiResponse.ok("Post saved to wishlist", Map.of("saved", true)));
    }

    // List all saved post IDs for current user
    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<List<Long>>> getMySavedPostIds(HttpServletRequest request) {
        Long userId = extractUserIdFromRequest(request);
        List<Long> postIds = savedPostRepository.findAllByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(sp -> sp.getPost().getId())
                .toList();
        return ResponseEntity.ok(ApiResponse.ok("Saved posts retrieved", postIds));
    }
}
