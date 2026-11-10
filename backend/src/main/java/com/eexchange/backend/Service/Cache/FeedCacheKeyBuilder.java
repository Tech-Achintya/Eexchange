package com.eexchange.backend.Service.Cache;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Optional;

@Component
public class FeedCacheKeyBuilder {

    /**
     * Builds the cache key from already-normalized feed parameters.
     * Sorting, paging and sizing are normalized by {@code PostQueryService} so the
     * cache and the database query always use exactly the same values.
     */
    public Optional<String> buildKey(long collegeVersion,
                                     Long collegeId,
                                     Long categoryId,
                                     String q,
                                     BigDecimal minPrice,
                                     BigDecimal maxPrice,
                                     String sort,
                                     int page,
                                     int size) {

        if (q != null && !q.trim().isEmpty()) {
            return Optional.empty(); // Bypassed when search query is non-blank
        }

        long normCollegeId = collegeId != null ? collegeId : 0L;
        String normCategory = categoryId != null ? String.valueOf(categoryId) : "all";
        String normSort = (sort == null || sort.isBlank()) ? "newest" : sort;
        int normPage = Math.max(0, page);
        int normSize = size > 0 ? size : 12;
        String normMinPrice = minPrice == null ? "min" : canonicalPrice(minPrice);
        String normMaxPrice = maxPrice == null ? "max" : canonicalPrice(maxPrice);

        // Format: feed:col:{collegeId}:v{version}:cat:{cat}:sort:{sort}:p:{page}:s:{size}:pr:{min}_{max}
        String key = String.format("feed:col:%d:v%d:cat:%s:sort:%s:p:%d:s:%d:pr:%s_%s",
                normCollegeId, collegeVersion, normCategory, normSort,
                normPage, normSize, normMinPrice, normMaxPrice);

        return Optional.of(key);
    }

    private String canonicalPrice(BigDecimal price) {
        return price.stripTrailingZeros().toPlainString();
    }
}
