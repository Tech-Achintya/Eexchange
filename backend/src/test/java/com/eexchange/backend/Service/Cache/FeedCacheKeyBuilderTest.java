package com.eexchange.backend.Service.Cache;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class FeedCacheKeyBuilderTest {

    private final FeedCacheKeyBuilder builder = new FeedCacheKeyBuilder();

    @Test
    void identicalParametersProduceTheSameKey() {
        String first = key(null, null, null, null, "newest", 0, 12);
        String second = key(null, null, null, null, "newest", 0, 12);

        assertThat(first).isEqualTo(second);
    }

    @Test
    void everyParameterChangesTheKey() {
        String base = key(1L, 2L, new BigDecimal("10"), new BigDecimal("20"),
                "newest", 0, 12);

        assertThat(key(1L, 3L, new BigDecimal("10"), new BigDecimal("20"),
                "newest", 0, 12)).isNotEqualTo(base);
        assertThat(key(1L, 2L, new BigDecimal("11"), new BigDecimal("20"),
                "newest", 0, 12)).isNotEqualTo(base);
        assertThat(key(1L, 2L, new BigDecimal("10"), new BigDecimal("21"),
                "newest", 0, 12)).isNotEqualTo(base);
        assertThat(key(1L, 2L, new BigDecimal("10"), new BigDecimal("20"),
                "priceasc", 0, 12)).isNotEqualTo(base);
        assertThat(key(1L, 2L, new BigDecimal("10"), new BigDecimal("20"),
                "newest", 1, 12)).isNotEqualTo(base);
        assertThat(key(1L, 2L, new BigDecimal("10"), new BigDecimal("20"),
                "newest", 0, 24)).isNotEqualTo(base);
    }

    @Test
    void pricesAreCanonicalized() {
        String trailingZeros = key(null, null, new BigDecimal("10.00"),
                new BigDecimal("20.50"), "newest", 0, 12);
        String shortForm = key(null, null, new BigDecimal("10"),
                new BigDecimal("20.5"), "newest", 0, 12);

        assertThat(trailingZeros).isEqualTo(shortForm);
    }

    @Test
    void zeroMaxPriceIsNotTheSameAsNoMaximum() {
        String zero = key(null, null, null, BigDecimal.ZERO, "newest", 0, 12);
        String noMaximum = key(null, null, null, null, "newest", 0, 12);

        assertThat(zero).isNotEqualTo(noMaximum);
    }

    @Test
    void categoryZeroIsNotTheSameAsNoCategory() {
        String zero = key(null, 0L, null, null, "newest", 0, 12);
        String all = key(null, null, null, null, "newest", 0, 12);

        assertThat(zero).isNotEqualTo(all);
    }

    @Test
    void searchQueryBypassesTheCache() {
        assertThat(builder.buildKey(1, 1L, null, "laptop", null, null,
                "newest", 0, 12)).isEmpty();
        assertThat(builder.buildKey(1, 1L, null, "   ", null, null,
                "newest", 0, 12)).isPresent();
    }

    private String key(Long collegeId,
                       Long categoryId,
                       BigDecimal minPrice,
                       BigDecimal maxPrice,
                       String sort,
                       int page,
                       int size) {
        Optional<String> key = builder.buildKey(1, collegeId, categoryId, null,
                minPrice, maxPrice, sort, page, size);
        assertThat(key).isPresent();
        return key.get();
    }
}
