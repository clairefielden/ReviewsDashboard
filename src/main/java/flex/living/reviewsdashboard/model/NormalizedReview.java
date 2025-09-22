package flex.living.reviewsdashboard.model;

import java.time.OffsetDateTime;
import java.util.Map;

public record NormalizedReview(
        String id,
        String listingName,
        String guestName,
        String direction,      // "host_to_guest" | "guest_to_host"
        String status,         // "published", "awaiting", etc.
        Integer overallRating, // null if not present
        Map<String, Integer> categoryRatings, // e.g. cleanliness:10
        String channel,        // e.g. "airbnb", "vrbo", "booking", "hostaway", "unknown"
        String text,
        OffsetDateTime submittedAt
) {
}
