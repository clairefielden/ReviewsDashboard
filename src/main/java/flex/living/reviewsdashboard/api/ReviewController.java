package flex.living.reviewsdashboard.api;

import flex.living.reviewsdashboard.model.NormalizedReviewResponse;
import flex.living.reviewsdashboard.service.HostawayReviewService;
import flex.living.reviewsdashboard.service.ReviewsAggregationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping(path = "/api/reviews", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Slf4j
public class ReviewController {

    private static final int DEFAULT_LIMIT = 50;
    private static final int DEFAULT_OFFSET = 0;
    private static final int MAX_LIMIT = 1000;

    private final HostawayReviewService hostawayService;
    private final ReviewsAggregationService aggregationService;

    /**
     * GET /api/reviews/hostaway?limit=50&offset=0
     */
    @GetMapping("/hostaway")
    public ResponseEntity<NormalizedReviewResponse> hostaway(
            @RequestParam(value = "limit", required = false) Integer limit,
            @RequestParam(value = "offset", required = false) Integer offset) {

        int lim = normalizeLimit(limit);
        int off = normalizeOffset(offset);

        try {
            return ResponseEntity.ok(hostawayService.getReviews(lim, off));
        } catch (Exception e) {
            log.warn("Hostaway fetch failed (limit={}, offset={}) → returning empty payload", lim, off, e);
            return ResponseEntity.ok(new NormalizedReviewResponse("hostaway", 0, List.of()));
        }
    }

    /**
     * GET /api/reviews/combined?limit=500&offset=0
     */
    @GetMapping("/combined")
    public ResponseEntity<NormalizedReviewResponse> combined(
            @RequestParam(value = "limit", defaultValue = "500") Integer limit,
            @RequestParam(value = "offset", defaultValue = "0") Integer offset) {

        int lim = normalizeLimit(limit);
        int off = normalizeOffset(offset);

        try {
            return ResponseEntity.ok(aggregationService.combined(lim, off));
        } catch (Exception e) {
            log.warn("Combined fetch failed (limit={}, offset={}) → returning empty payload", lim, off, e);
            return ResponseEntity.ok(new NormalizedReviewResponse("combined", 0, List.of()));
        }
    }

    // -------- helpers --------
    private static int normalizeLimit(Integer limit) {
        int lim = (limit == null) ? DEFAULT_LIMIT : limit;
        if (lim < 1) lim = DEFAULT_LIMIT;
        if (lim > MAX_LIMIT) lim = MAX_LIMIT;
        return lim;
    }

    private static int normalizeOffset(Integer offset) {
        int off = (offset == null) ? DEFAULT_OFFSET : offset;
        if (off < 0) off = DEFAULT_OFFSET;
        return off;
    }
}
