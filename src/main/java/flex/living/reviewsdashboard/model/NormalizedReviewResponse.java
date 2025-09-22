package flex.living.reviewsdashboard.model;

import java.util.List;

public record NormalizedReviewResponse(
        String source,    // "hostaway"
        int count,
        List<NormalizedReview> reviews
) {
}