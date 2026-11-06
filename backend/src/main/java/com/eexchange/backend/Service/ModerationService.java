package com.eexchange.backend.Service;

import java.math.BigDecimal;
import java.util.List;

public interface ModerationService {

    ModerationResult review(PostModerationContext context);

    record PostModerationContext(
            Long postId,
            String title,
            String description,
            BigDecimal price,
            List<String> imageUrls
    ) {
        public List<String> getImageUrls(){
            return imageUrls;
        }
        public String getTitle(){
            return title;
        }
        public String getDescription(){
            return description;
        }
        public BigDecimal getPrice(){
            return price;
        }
    }
    enum Outcome {
        APPROVED,
        REJECTED,
        RETRY_LATER,
        DEFERRED
    }

    // Fixed categories only: never raw model text or response bodies.
    enum FailureCategory {
        HTTP_ERROR,
        TIMEOUT,
        MALFORMED_ANSWER
    }

    // code is only set for REJECTED; failure is only set for RETRY_LATER.
    record ModerationResult(Outcome outcome, String code, FailureCategory failure) {

        public static ModerationResult approved() {
            return new ModerationResult(Outcome.APPROVED, null, null);
        }

        public static ModerationResult rejected(String code) {
            return new ModerationResult(Outcome.REJECTED, code, null);
        }

        public static ModerationResult retryLater() {
            return retryLater(FailureCategory.HTTP_ERROR);
        }

        public static ModerationResult retryLater(FailureCategory failure) {
            return new ModerationResult(Outcome.RETRY_LATER, null, failure);
        }

        // Daily budget exhausted: not a failed attempt, so the post is untouched.
        public static ModerationResult deferred() {
            return new ModerationResult(Outcome.DEFERRED, null, null);
        }
    }
}