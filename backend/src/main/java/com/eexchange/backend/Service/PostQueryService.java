package com.eexchange.backend.Service;

import com.eexchange.backend.DTO.Response.PageResponse;
import com.eexchange.backend.DTO.Response.PostCardResponse;
import com.eexchange.backend.DTO.Response.PostDetailResponse;
import com.eexchange.backend.Entity.Enums.PostStatus;
import com.eexchange.backend.Entity.Post;
import com.eexchange.backend.Entity.PostImage;
import com.eexchange.backend.Entity.User;
import com.eexchange.backend.Exception.AppException;
import com.eexchange.backend.Repository.PostImageRepository;
import com.eexchange.backend.Repository.PostRepository;
import com.eexchange.backend.Repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import com.eexchange.backend.Repository.InterestMessageRepository;
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostQueryService {

    private static final int DEFAULT_PAGE_SIZE = 12;
    private static final int MAX_PAGE_SIZE = 50;

    private final UserRepository userRepository;
    private final PostRepository postRepository;
    private final PostImageRepository postImageRepository;
    private final StorageService storageService;
    private final InterestMessageRepository interestMessageRepository;
    // ---------------------------------------------------------------- feed

    public PageResponse<PostCardResponse> feed(Long userId,
                                               Long categoryId,
                                               String q,
                                               BigDecimal minPrice,
                                               BigDecimal maxPrice,
                                               String sort,
                                               int page,
                                               int size) {

        // College comes from the DB user, never from the JWT claim.
        User user = requireUser(userId);

        Pageable pageable = PageRequest.of(
                normalizePage(page), normalizeSize(size), resolveSort(sort));

        Page<Post> posts = postRepository.findFeed(
                user.getCollege().getId(),
                PostStatus.APPROVED,
                Instant.now(),
                categoryId,
                likePattern(q),
                minPrice,
                maxPrice,
                pageable);

        if (posts.getContent().isEmpty()) {
            return pageResponse(posts, List.of());
        }

        Map<Long, String> firstImageKeys = postImageRepository
                .findFirstImagesByPostIds(ids(posts.getContent()))
                .stream()
                .collect(Collectors.toMap(
                        image -> image.getPost().getId(),
                        PostImage::getUrl,
                        (first, ignored) -> first));

        List<PostCardResponse> content = posts.getContent().stream()
                .map(post -> toCard(post, firstImageKeys.get(post.getId())))
                .toList();

        return pageResponse(posts, content);
    }

    // -------------------------------------------------------------- detail

    public PostDetailResponse detail(Long userId, Long postId) {
        User user = requireUser(userId);

        Post post = postRepository.findDetailById(postId)
                .orElseThrow(this::notFound);

        boolean owner = post.getUser().getId().equals(user.getId());

        if (owner) {
            // Owner sees every status except DELETED.
            if (post.getStatus() == PostStatus.DELETED) {
                throw notFound();
            }
        } else {
            boolean sameCollege = post.getCollege().getId()
                    .equals(user.getCollege().getId());
            boolean visible = sameCollege
                    && post.getStatus() == PostStatus.APPROVED
                    && !isExpired(post);
            if (!visible) {
                // Never 403: do not confirm that the post exists.
                throw notFound();
            }
        }

        // Only reachable for a visible post; owners never get the flag.
        boolean interested = !owner
                && interestMessageRepository.existsByPostIdAndSenderId(
                post.getId(), user.getId());

        List<PostImage> images = postImageRepository
                .findByPostIdInOrderByPostIdAscPositionAsc(List.of(post.getId()));

        return toDetail(post, images, interested);
    }

    // ---------------------------------------------------------------- mine

    public PageResponse<PostDetailResponse> mine(Long userId,
                                                 PostStatus status,
                                                 int page,
                                                 int size) {

        requireUser(userId);

        Pageable pageable = PageRequest.of(
                normalizePage(page),
                normalizeSize(size),
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));

        Page<Post> posts = postRepository.findMine(
                userId, PostStatus.DELETED, status, pageable);

        // An empty page must not run "where id in ()" against Postgres.
        if (posts.getContent().isEmpty()) {
            return pageResponse(posts, List.of());
        }

        // One extra query for all images on this page (no N+1).
        Map<Long, List<PostImage>> imagesByPost = postImageRepository
                .findByPostIdInOrderByPostIdAscPositionAsc(ids(posts.getContent()))
                .stream()
                .collect(Collectors.groupingBy(image -> image.getPost().getId()));

        List<PostDetailResponse> content = posts.getContent().stream()
                .map(post -> toDetail(
                        post,
                        imagesByPost.getOrDefault(post.getId(), List.of()),
                        false))
                .toList();

        return pageResponse(posts, content);
    }

    // ------------------------------------------------------------- helpers

    private User requireUser(Long userId) {
        return userRepository.findById(userId)
                .filter(user -> user.getDeletedAt() == null)
                .orElseThrow(() -> new AppException(
                        "Authentication required", HttpStatus.UNAUTHORIZED));
    }

    private PostCardResponse toCard(Post post, String firstImageKey) {
        return new PostCardResponse(
                post.getId(),
                post.getTitle(),
                post.getPrice(),
                post.getCondition(),
                firstImageKey == null ? null : storageService.publicUrl(firstImageKey),
                post.getCategory().getName(),
                post.getUser().getName(),
                post.getCreatedAt());
    }

    private PostDetailResponse toDetail(Post post,
                                        List<PostImage> images,
                                        boolean interested) {
        return new PostDetailResponse(
                post.getId(),
                post.getTitle(),
                post.getDescription(),
                post.getPrice(),
                post.getCondition(),
                post.getCategory().getId(),
                post.getCategory().getName(),
                post.getStatus(),
                post.getRejectionReason(),
                post.getCollege().getId(),
                post.getUser().getId(),
                post.getUser().getName(),
                images.stream()
                        .map(image -> storageService.publicUrl(image.getUrl()))
                        .toList(),
                post.getCreatedAt(),
                post.getExpiresAt(),
                interested
        );
    }

    private boolean isExpired(Post post) {
        return post.getExpiresAt() != null
                && !post.getExpiresAt().isAfter(Instant.now());
    }

    private Sort resolveSort(String sort) {
        if (sort == null || sort.isBlank() || sort.equalsIgnoreCase("newest")) {
            return Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
        }

        return switch (sort.toLowerCase(Locale.ROOT)) {
            case "priceasc" -> Sort.by(
                    Sort.Order.asc("price"), Sort.Order.desc("id"));
            case "pricedesc" -> Sort.by(
                    Sort.Order.desc("price"), Sort.Order.desc("id"));
            default -> throw new AppException(
                    "Invalid sort. Use newest, priceAsc or priceDesc",
                    HttpStatus.BAD_REQUEST);
        };
    }

    // Escapes LIKE wildcards; '!' is the escape character used in the JPQL.
    private String likePattern(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String escaped = raw.trim().toLowerCase(Locale.ROOT)
                .replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");
        return "%" + escaped + "%";
    }

    private int normalizePage(int page) {
        return Math.max(page, 0);
    }

    private int normalizeSize(int size) {
        if (size <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }

    private List<Long> ids(List<Post> posts) {
        return posts.stream().map(Post::getId).toList();
    }

    private <T> PageResponse<T> pageResponse(Page<?> page, List<T> content) {
        return new PageResponse<>(
                content,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }

    private AppException notFound() {
        return new AppException("Post not found", HttpStatus.NOT_FOUND);
    }
}