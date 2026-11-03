package com.eexchange.backend.Service;

import com.eexchange.backend.DTO.Request.PostCreateRequest;
import com.eexchange.backend.DTO.Request.PostUpdateRequest;
import com.eexchange.backend.DTO.Response.PostResponse;
import com.eexchange.backend.Entity.Category;
import com.eexchange.backend.Entity.Enums.ItemCondition;
import com.eexchange.backend.Entity.Enums.ListingType;
import com.eexchange.backend.Entity.Enums.PostStatus;
import com.eexchange.backend.Entity.Post;
import com.eexchange.backend.Entity.PostImage;
import com.eexchange.backend.Entity.User;
import com.eexchange.backend.Exception.AppException;
import com.eexchange.backend.Repository.CategoryRepository;
import com.eexchange.backend.Repository.PostRepository;
import com.eexchange.backend.Repository.UserRepository;
import com.eexchange.backend.Service.Event.PostCreatedEvent;
import com.eexchange.backend.Service.Storage.ImageType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@RequiredArgsConstructor
@Slf4j
public class PostService {

    private static final int MAX_IMAGE_BYTES = 5 * 1024 * 1024;

    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final PostRepository postRepository;
    private final StorageService storageService;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${app.post.expiry-days:60}")
    private long expiryDays;

    @Value("${app.post.max-images:5}")
    private int maxImages;

    // ------------------------------------------------------------- create

    @Transactional
    public PostResponse createPost(Long userId,
                                   PostCreateRequest request,
                                   List<MultipartFile> files) {

        User user = requireUser(userId);
        ValidPost fields = validatePostFields(request);
        List<PreparedImage> prepared = prepareImages(files);

        if (prepared.isEmpty()) {
            throw new AppException("At least one image is required",
                    HttpStatus.BAD_REQUEST);
        }
        if (prepared.size() > maxImages) {
            throw new AppException("A post can have at most " + maxImages + " images",
                    HttpStatus.BAD_REQUEST);
        }

        List<String> uploadedKeys = new ArrayList<>();
        AtomicBoolean cleanedUp = new AtomicBoolean(false);

        try {
            for (PreparedImage image : prepared) {
                uploadedKeys.add(storageService.store(
                        user.getId(), image.bytes(), image.type()));
            }

            Post post = Post.builder()
                    .user(user)
                    .college(user.getCollege())
                    .category(fields.category())
                    .title(fields.title())
                    .description(fields.description())
                    .listingType(fields.listingType())
                    .price(fields.price())
                    .exchangeWanted(fields.exchangeWanted())
                    .condition(fields.condition())
                    .status(PostStatus.PENDING_REVIEW)
                    .expiresAt(Instant.now().plus(expiryDays, ChronoUnit.DAYS))
                    .build();

            for (int index = 0; index < uploadedKeys.size(); index++) {
                post.getImages().add(PostImage.builder()
                        .post(post)
                        .url(uploadedKeys.get(index))
                        .position((short) index)
                        .build());
            }

            Post saved = postRepository.saveAndFlush(post);

            registerRollbackCleanup(uploadedKeys, cleanedUp);
            eventPublisher.publishEvent(new PostCreatedEvent(saved.getId()));

            return toResponse(saved);
        } catch (RuntimeException ex) {
            if (cleanedUp.compareAndSet(false, true)) {
                deleteQuietly(uploadedKeys);
            }
            throw ex;
        }
    }

    // ------------------------------------------------------------- update

    @Transactional
    public PostResponse updatePost(Long userId,
                                   Long postId,
                                   PostUpdateRequest request,
                                   List<MultipartFile> newFiles) {

        User user = requireUser(userId);
        Post post = requireOwnedPost(userId, postId);

        if (post.getStatus() == PostStatus.SOLD || post.getStatus() == PostStatus.DELETED) {
            throw new AppException("This post can no longer be edited",
                    HttpStatus.BAD_REQUEST);
        }

        // Same validator as create: title, description, category, price rules.
        ValidPost fields = validatePostFields(request);

        List<PreparedImage> prepared = prepareImages(newFiles);

        Set<Long> removeIds = request.getRemoveImageIds() == null
                ? Set.of()
                : new LinkedHashSet<>(request.getRemoveImageIds());

        List<PostImage> current = post.getImages();
        Set<Long> currentIds = current.stream()
                .map(PostImage::getId)
                .collect(java.util.stream.Collectors.toSet());

        // Every id must belong to this post.
        if (!currentIds.containsAll(removeIds)) {
            throw new AppException("One or more images do not belong to this post",
                    HttpStatus.BAD_REQUEST);
        }

        long remaining = current.size() - removeIds.size() + prepared.size();
        if (remaining < 1) {
            throw new AppException("A post must keep at least one image",
                    HttpStatus.BAD_REQUEST);
        }
        if (remaining > maxImages) {
            throw new AppException("A post can have at most " + maxImages + " images",
                    HttpStatus.BAD_REQUEST);
        }

        List<String> removedKeys = current.stream()
                .filter(image -> removeIds.contains(image.getId()))
                .map(PostImage::getUrl)
                .toList();

        List<String> uploadedKeys = new ArrayList<>();
        AtomicBoolean cleanedUp = new AtomicBoolean(false);

        try {
            for (PreparedImage image : prepared) {
                uploadedKeys.add(storageService.store(
                        user.getId(), image.bytes(), image.type()));
            }

            // Orphan removal deletes these post_images rows at flush.
            current.removeIf(image -> removeIds.contains(image.getId()));

            int position = 0;
            for (PostImage image : current) {
                image.setPosition((short) position++);
            }
            for (String key : uploadedKeys) {
                current.add(PostImage.builder()
                        .post(post)
                        .url(key)
                        .position((short) position++)
                        .build());
            }

            post.setCategory(fields.category());
            post.setTitle(fields.title());
            post.setDescription(fields.description());
            post.setListingType(fields.listingType());
            post.setPrice(fields.price());
            post.setExchangeWanted(fields.exchangeWanted());
            post.setCondition(fields.condition());

            // Any edit needs a fresh AI review.
            post.setStatus(PostStatus.PENDING_REVIEW);
            post.setRejectionReason(null);

            Post saved = postRepository.saveAndFlush(post);

            // Removed objects disappear only if the transaction commits.
            deleteAfterCommit(removedKeys, "removed post image");
            // New objects disappear if it rolls back.
            registerRollbackCleanup(uploadedKeys, cleanedUp);

            eventPublisher.publishEvent(new PostCreatedEvent(saved.getId()));

            return toResponse(saved);
        } catch (RuntimeException ex) {
            if (cleanedUp.compareAndSet(false, true)) {
                deleteQuietly(uploadedKeys);
            }
            throw ex;
        }
    }

    // --------------------------------------------------------------- sold

    @Transactional
    public PostResponse markSold(Long userId, Long postId) {
        requireUser(userId);
        Post post = requireOwnedPost(userId, postId);

        if (post.getStatus() != PostStatus.APPROVED) {
            throw new AppException("Only approved posts can be marked as sold",
                    HttpStatus.BAD_REQUEST);
        }

        post.setStatus(PostStatus.SOLD);
        return toResponse(postRepository.saveAndFlush(post));
    }

    // ------------------------------------------------------------- delete

    @Transactional
    public void deletePost(Long userId, Long postId) {
        requireUser(userId);
        Post post = requireOwnedPost(userId, postId);

        if (post.getStatus() == PostStatus.DELETED) {
            throw notFound();
        }

        List<String> keys = post.getImages().stream()
                .map(PostImage::getUrl)
                .toList();

        post.setStatus(PostStatus.DELETED);
        postRepository.save(post);

        deleteAfterCommit(keys, "deleted post image");
    }

    // ------------------------------------------------------------ shared

    // ONE validator for create and update, so the DB price rule cannot drift.
    private ValidPost validatePostFields(PostCreateRequest request) {
        if (request.getCategoryId() == null) {
            throw new AppException("Category is required", HttpStatus.BAD_REQUEST);
        }

        Category category = categoryRepository.findById(request.getCategoryId())
                .filter(candidate -> Boolean.TRUE.equals(candidate.getIsActive()))
                .orElseThrow(() -> new AppException(
                        "Category not found or inactive", HttpStatus.BAD_REQUEST));

        if (request.getListingType() == null) {
            throw new AppException("Listing type is required", HttpStatus.BAD_REQUEST);
        }
        if (request.getCondition() == null) {
            throw new AppException("Condition is required", HttpStatus.BAD_REQUEST);
        }

        String title = trimToEmpty(request.getTitle());
        String description = trimToEmpty(request.getDescription());

        if (title.length() < 5 || title.length() > 150) {
            throw new AppException("Title must be between 5 and 150 characters",
                    HttpStatus.BAD_REQUEST);
        }
        if (description.length() < 10 || description.length() > 2000) {
            throw new AppException("Description must be between 10 and 2000 characters",
                    HttpStatus.BAD_REQUEST);
        }

        BigDecimal price = request.getPrice();
        String exchangeWanted = trimToNull(request.getExchangeWanted());

        switch (request.getListingType()) {
            case SELL -> {
                if (price == null || price.signum() < 0) {
                    throw new AppException("SELL posts require a price of 0 or more",
                            HttpStatus.BAD_REQUEST);
                }
                if (exchangeWanted != null) {
                    throw new AppException("SELL posts cannot have an exchangeWanted value",
                            HttpStatus.BAD_REQUEST);
                }
            }
            case FREE -> {
                if (price != null) {
                    throw new AppException("FREE posts cannot have a price",
                            HttpStatus.BAD_REQUEST);
                }
                if (exchangeWanted != null) {
                    throw new AppException("FREE posts cannot have an exchangeWanted value",
                            HttpStatus.BAD_REQUEST);
                }
            }
            case EXCHANGE -> {
                if (price != null) {
                    throw new AppException("EXCHANGE posts cannot have a price",
                            HttpStatus.BAD_REQUEST);
                }
                if (exchangeWanted == null) {
                    throw new AppException("EXCHANGE posts require an exchangeWanted value",
                            HttpStatus.BAD_REQUEST);
                }
            }
        }

        return new ValidPost(title, description, category, request.getListingType(),
                price, exchangeWanted, request.getCondition());
    }

    // Reads, size-checks and magic-byte checks. Never trusts the filename.
    private List<PreparedImage> prepareImages(List<MultipartFile> files) {
        if (files == null) {
            return List.of();
        }

        List<PreparedImage> prepared = new ArrayList<>();

        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                continue;
            }
            if (file.getSize() > MAX_IMAGE_BYTES) {
                throw new AppException("Each image must be at most 5 MB",
                        HttpStatus.PAYLOAD_TOO_LARGE);
            }

            byte[] bytes;
            try {
                bytes = file.getBytes();
            } catch (IOException ex) {
                throw new AppException("Could not read the uploaded image",
                        HttpStatus.BAD_REQUEST);
            }

            ImageType imageType = ImageType.detect(bytes)
                    .orElseThrow(() -> new AppException(
                            "Only JPEG, PNG or WebP images are allowed",
                            HttpStatus.BAD_REQUEST));

            prepared.add(new PreparedImage(bytes, imageType));
        }

        return prepared;
    }

    private User requireUser(Long userId) {
        return userRepository.findById(userId)
                .filter(candidate -> candidate.getDeletedAt() == null)
                .orElseThrow(() -> new AppException("User not found",
                        HttpStatus.UNAUTHORIZED));
    }

    private Post requireOwnedPost(Long userId, Long postId) {
        Post post = postRepository.findDetailById(postId)
                .orElseThrow(this::notFound);

        if (!post.getUser().getId().equals(userId)) {
            // Never 403: do not confirm that someone else's post exists.
            throw notFound();
        }
        return post;
    }

    private void deleteAfterCommit(List<String> keys, String what) {
        if (keys.isEmpty()) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            deleteQuietly(keys);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        log.debug("Deleting {} storage objects ({})", keys.size(), what);
                        deleteQuietly(keys);
                    }
                });
    }

    private void registerRollbackCleanup(List<String> uploadedKeys,
                                         AtomicBoolean cleanedUp) {
        if (uploadedKeys.isEmpty()
                || !TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int status) {
                        if (status == TransactionSynchronization.STATUS_ROLLED_BACK
                                && cleanedUp.compareAndSet(false, true)) {
                            deleteQuietly(uploadedKeys);
                        }
                    }
                });
    }

    // Storage must never break an API call; failures are only logged.
    private void deleteQuietly(List<String> keys) {
        for (String key : keys) {
            try {
                storageService.delete(key);
            } catch (RuntimeException ex) {
                log.warn("Failed to delete storage object {}; continuing", key, ex);
            }
        }
    }

    private PostResponse toResponse(Post post) {
        List<PostResponse.PostImageResponse> images = post.getImages().stream()
                .map(image -> new PostResponse.PostImageResponse(
                        image.getId(),
                        storageService.publicUrl(image.getUrl()),
                        image.getPosition()))
                .toList();

        return new PostResponse(
                post.getId(),
                post.getTitle(),
                post.getDescription(),
                post.getPrice(),
                post.getListingType(),
                post.getExchangeWanted(),
                post.getCondition(),
                post.getCategory().getId(),
                post.getCategory().getName(),
                post.getStatus(),
                post.getCollege().getId(),
                post.getUser().getId(),
                post.getUser().getName(),
                images,
                post.getCreatedAt(),
                post.getExpiresAt());
    }

    private AppException notFound() {
        return new AppException("Post not found", HttpStatus.NOT_FOUND);
    }

    private String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private record ValidPost(String title,
                             String description,
                             Category category,
                             ListingType listingType,
                             BigDecimal price,
                             String exchangeWanted,
                             ItemCondition condition) {
    }

    private record PreparedImage(byte[] bytes, ImageType type) {
    }
}