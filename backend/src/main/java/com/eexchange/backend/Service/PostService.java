package com.eexchange.backend.Service;

import com.eexchange.backend.DTO.Request.PostCreateRequest;
import com.eexchange.backend.DTO.Response.PostResponse;
import com.eexchange.backend.Entity.Category;
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
import java.util.List;
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

    @Transactional
    public PostResponse createPost(Long userId,
                                   PostCreateRequest request,
                                   List<MultipartFile> files) {

        // College comes from the DB-loaded user, not from the JWT claim.
        User user = userRepository.findById(userId)
                .filter(candidate -> candidate.getDeletedAt() == null)
                .orElseThrow(() -> new AppException("User not found", HttpStatus.UNAUTHORIZED));

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

        Pricing pricing = validatePricing(request);

        List<MultipartFile> imageFiles = files == null
                ? List.of()
                : files.stream()
                .filter(file -> file != null && !file.isEmpty())
                .toList();

        // at least one image is required.
        if (imageFiles.isEmpty()) {
            throw new AppException("At least one image is required",
                    HttpStatus.BAD_REQUEST);
        }
        if (imageFiles.size() > maxImages) {
            throw new AppException("A post can have at most " + maxImages + " images",
                    HttpStatus.BAD_REQUEST);
        }

        List<ImagePayload> payloads = new ArrayList<>();

        for (MultipartFile file : imageFiles) {
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

            payloads.add(new ImagePayload(bytes, imageType));
        }

        List<String> uploadedKeys = new ArrayList<>();
        AtomicBoolean cleanedUp = new AtomicBoolean(false);

        try {
            for (ImagePayload payload : payloads) {
                uploadedKeys.add(storageService.store(
                        user.getId(), payload.bytes(), payload.type()));
            }

            Post post = Post.builder()
                    .user(user)
                    .college(user.getCollege())
                    .category(category)
                    .title(title)
                    .description(description)
                    .listingType(request.getListingType())
                    .price(pricing.price())
                    .exchangeWanted(pricing.exchangeWanted())
                    .condition(request.getCondition())
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

            // Covers commit-time failures that happen after this method returns.
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

    private Pricing validatePricing(PostCreateRequest request) {
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

        return new Pricing(price, exchangeWanted);
    }

    private void registerRollbackCleanup(List<String> uploadedKeys,
                                         AtomicBoolean cleanedUp) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
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

    private void deleteQuietly(List<String> keys) {
        for (String key : keys) {
            try {
                storageService.delete(key);
            } catch (RuntimeException ex) {
                log.warn("Failed to delete orphaned storage object {}", key, ex);
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

    private record Pricing(BigDecimal price, String exchangeWanted) {
    }

    private record ImagePayload(byte[] bytes, ImageType type) {
    }
}