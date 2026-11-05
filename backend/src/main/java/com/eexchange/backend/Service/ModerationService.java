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
        RETRY_LATER
    }

    // code is only set for REJECTED; never contains free-form model text.
    record ModerationResult(Outcome outcome, String code) {

        public static ModerationResult approved() {
            return new ModerationResult(Outcome.APPROVED, null);
        }

        public static ModerationResult rejected(String code) {
            return new ModerationResult(Outcome.REJECTED, code);
        }

        public static ModerationResult retryLater() {
            return new ModerationResult(Outcome.RETRY_LATER, null);
        }
    }
}