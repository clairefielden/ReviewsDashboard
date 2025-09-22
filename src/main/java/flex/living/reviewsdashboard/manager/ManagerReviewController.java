package flex.living.reviewsdashboard.manager;

import flex.living.reviewsdashboard.model.NormalizedReview;
import flex.living.reviewsdashboard.model.NormalizedReviewResponse;
import flex.living.reviewsdashboard.service.HostawayReviewService;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@RestController
@RequestMapping("/api/manager")
public class ManagerReviewController {

    private final HostawayReviewService svc;
    private final ReviewSelectionStore store;

    public ManagerReviewController(HostawayReviewService svc, ReviewSelectionStore store) {
        this.svc = svc;
        this.store = store;
    }

    /**
     * Filtered list for managers (server-side filtering mirrors the UI).
     */
    @GetMapping("/reviews")
    public NormalizedReviewResponse list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String channel,
            @RequestParam(required = false) String listing,
            @RequestParam(required = false) Integer ratingMin,
            @RequestParam(required = false) Integer ratingMax,
            @RequestParam(required = false) OffsetDateTime from,
            @RequestParam(required = false) OffsetDateTime to
    ) throws IOException {
        var base = svc.getReviews(200, 0).reviews(); // pull a batch; expand/paginate if needed

        var filtered = base.stream().filter(r -> {
            if (q != null && !q.isBlank()) {
                var needle = q.toLowerCase();
                if (!safe(r.text()).contains(needle) &&
                        !safe(r.guestName()).contains(needle) &&
                        !safe(r.listingName()).contains(needle)) return false;
            }
            if (channel != null && !channel.isBlank() && !"all".equalsIgnoreCase(channel)) {
                if (!Objects.equals(r.channel(), channel)) return false;
            }
            if (listing != null && !listing.isBlank() && !"all".equalsIgnoreCase(listing)) {
                if (!Objects.equals(r.listingName(), listing)) return false;
            }
            var rating = r.overallRating();
            if (rating == null && r.categoryRatings() != null && !r.categoryRatings().isEmpty()) {
                rating = (int) Math.round(r.categoryRatings().values().stream().mapToInt(Integer::intValue).average().orElse(Double.NaN));
            }
            if (ratingMin != null && rating != null && rating < ratingMin) return false;
            if (ratingMax != null && rating != null && rating > ratingMax) return false;

            if (from != null && r.submittedAt() != null && r.submittedAt().isBefore(from)) return false;
            return to == null || r.submittedAt() == null || !r.submittedAt().isAfter(to);
        }).toList();

        return new NormalizedReviewResponse("hostaway", filtered.size(), filtered);
    }

    /**
     * Toggle whether a review is approved for the public web.
     */
    @PatchMapping("/reviews/{id}/selection")
    public Map<String, Object> toggle(@PathVariable String id, @RequestBody SelectionToggle body) {
        store.setSelected(id, body.selected());
        return Map.of("id", id, "selected", store.isSelected(id));
    }

    @GetMapping(value = "/reviews/selected", produces = "application/json")
    public List<NormalizedReview> selected() throws IOException {
        var ids = store.all();
        return svc.getReviews(200, 0).reviews()
                .stream()
                .filter(r -> ids.contains(r.id()))
                .toList(); // -> serializes to [] when empty
    }

    private static String safe(String s) {
        return s == null ? "" : s.toLowerCase();
    }
}
