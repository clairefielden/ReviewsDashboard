package flex.living.reviewsdashboard.service;

import flex.living.reviewsdashboard.client.GoogleReviewClient;
import flex.living.reviewsdashboard.client.HostawayReviewClient;
import flex.living.reviewsdashboard.config.ListingsConfig;
import flex.living.reviewsdashboard.model.NormalizedReview;
import flex.living.reviewsdashboard.model.NormalizedReviewResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReviewsAggregationService {

    private final HostawayReviewClient hostaway;
    private final GoogleReviewClient google;
    private final ListingsConfig listingsCfg;

    /**
     * Merge Hostaway + Google, set listingName on Google reviews via mapping, sort desc, slice.
     */
    public NormalizedReviewResponse combined(int limit, int offset) {
        List<NormalizedReview> merged = new ArrayList<>();

        try {
            merged.addAll(hostaway.fetchAndNormalize(limit, offset).reviews());
        } catch (Exception ignored) {
        }

        if (listingsCfg.getGooglePlaceIds() != null) {
            listingsCfg.getGooglePlaceIds().forEach((listingName, placeId) -> {
                try {
                    var resp = google.fetchForPlace(placeId);
                    for (var r : resp.reviews()) {
                        // attach the listing name so frontend grouping works
                        merged.add(new NormalizedReview(
                                r.id(), listingName, r.guestName(), r.direction(), r.status(),
                                r.overallRating(), r.categoryRatings(), r.channel(), r.text(), r.submittedAt()
                        ));
                    }
                } catch (Exception ignored) {
                }
            });
        }

        // sort newest first, then slice
        merged.sort((a, b) -> {
            OffsetDateTime da = a.submittedAt();
            OffsetDateTime db = b.submittedAt();
            long ca = da == null ? Long.MIN_VALUE : da.toInstant().toEpochMilli();
            long cb = db == null ? Long.MIN_VALUE : db.toInstant().toEpochMilli();
            return Long.compare(cb, ca);
        });

        int from = Math.max(0, Math.min(offset, merged.size()));
        int to = Math.max(from, Math.min(from + limit, merged.size()));
        List<NormalizedReview> page = merged.subList(from, to);

        return new NormalizedReviewResponse("combined", page.size(), page);
    }
}
