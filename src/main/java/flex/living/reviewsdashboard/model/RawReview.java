package flex.living.reviewsdashboard.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Mirrors the API payload (be generous with nullability).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class RawReview {
    public String id;

    @JsonProperty("listingId")
    public Long listingId;

    public String channel;        // e.g., AIRBNB, VRBO, BOOKING, DIRECT
    public String type;           // e.g., PUBLIC, PRIVATE, OWNER_RESPONSE
    public String reviewerName;
    public String comment;
    public Integer rating;        // 1..5 or null
    public String createdAt;      // string; we’ll normalize to LocalDate
    public String source;         // optional

    public RawReview() {
    }
}
