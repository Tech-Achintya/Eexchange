package com.eexchange.backend.Service.Cache;

import com.eexchange.backend.Configuration.FeedCacheProperties;
import com.eexchange.backend.DTO.Response.PageResponse;
import com.eexchange.backend.DTO.Response.PostCardResponse;
import com.eexchange.backend.Entity.Enums.ItemCondition;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FeedCacheServiceTest {

    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private CollegeFeedVersionService versionService;
    private ObjectMapper objectMapper;
    private FeedCacheService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        versionService = mock(CollegeFeedVersionService.class);
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        service = new FeedCacheService(redisTemplate, new FeedCacheKeyBuilder(),
                versionService, new FeedCacheProperties(), objectMapper);
    }

    @Test
    void cacheHitReturnsStoredPage() throws Exception {
        PageResponse<PostCardResponse> page = page(1L);
        when(valueOperations.get(anyString()))
                .thenReturn(objectMapper.writeValueAsString(page));

        Optional<PageResponse<PostCardResponse>> result = get(null);

        assertThat(result).isPresent();
        assertThat(result.get().content()).hasSize(1);
        assertThat(result.get().content().getFirst().id()).isEqualTo(1L);
    }

    @Test
    void cacheMissReturnsEmpty() {
        when(valueOperations.get(anyString())).thenReturn(null);

        assertThat(get(null)).isEmpty();
    }

    @Test
    void redisReadFailureFailsOpen() {
        when(valueOperations.get(anyString()))
                .thenThrow(new RedisConnectionFailureException("down"));

        assertThat(get(null)).isEmpty();
    }

    @Test
    void emptyPageIsNotStored() {
        put(new PageResponse<>(List.of(), 0, 12, 0, 0), null);

        verify(valueOperations, never())
                .setIfAbsent(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void ttlIsCappedByTheEarliestPostExpiry() {
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(true);

        put(page(1L), Instant.now().plusSeconds(1_200));

        ArgumentCaptor<Duration> ttl = ArgumentCaptor.forClass(Duration.class);
        verify(valueOperations).set(anyString(), anyString(), ttl.capture());

        assertThat(ttl.getValue().toSeconds()).isBetween(1_190L, 1_200L);
    }

    @Test
    void pageExpiringBelowMinTtlIsNotStored() {
        put(page(1L), Instant.now().plusSeconds(30));

        verify(valueOperations, never())
                .setIfAbsent(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void losingTheFillLockSkipsTheWrite() {
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(false);

        put(page(1L), null);

        verify(valueOperations, never())
                .set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void redisWriteFailureDoesNotEscape() {
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenThrow(new RedisConnectionFailureException("down"));

        put(page(1L), null);
    }

    @Test
    void searchQueryNeverReadsTheCache() {
        assertThat(get("laptop")).isEmpty();
        verify(valueOperations, never()).get(anyString());
    }

    private Optional<PageResponse<PostCardResponse>> get(String q) {
        return service.get(3, 1L, null, q, null, null, "newest", 0, 12);
    }

    private void put(PageResponse<PostCardResponse> page, Instant earliestExpiry) {
        service.put(3, 1L, null, null, null, null, "newest", 0, 12,
                page, earliestExpiry);
    }

    private PageResponse<PostCardResponse> page(Long postId) {
        PostCardResponse card = new PostCardResponse(
                postId,
                "Used laptop",
                new BigDecimal("250.00"),
                ItemCondition.GOOD,
                "https://cdn.test/a.jpg",
                "Electronics",
                "Seller",
                Instant.parse("2026-01-01T00:00:00Z"));

        return new PageResponse<>(List.of(card), 0, 12, 1, 1);
    }
}
