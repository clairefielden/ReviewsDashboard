package flex.living.reviewsdashboard.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import flex.living.reviewsdashboard.model.NormalizedReview;
import flex.living.reviewsdashboard.model.NormalizedReviewResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class HostawayReviewClient {

    private static final String MOCK_RESOURCE = "/mock-reviews.json";

    private final WebClient wc;
    private final HostawayAuthClient auth;
    private final ObjectMapper mapper = new ObjectMapper();

    public HostawayReviewClient(WebClient hostawayWebClient, HostawayAuthClient auth) {
        this.wc = hostawayWebClient;
        this.auth = auth;
    }

    /**
     * Orchestrates: call Hostaway → fallback to mock if empty/failed → normalize → wrap response.
     */
    public NormalizedReviewResponse fetchAndNormalize(Integer limit, Integer offset) throws IOException {
        List<Map<String, Object>> reviews;

        try {
            reviews = callReviews(limit, offset);
        } catch (WebClientResponseException e) {
            if (e.getStatusCode() == HttpStatus.FORBIDDEN) {
                // Refresh once, then retry
                auth.refreshToken();
                reviews = callReviews(limit, offset);
            } else {
                reviews = mockReviews();
            }
        } catch (Exception any) {
            reviews = mockReviews();
        }

        if (reviews == null || reviews.isEmpty()) {
            reviews = mockReviews();
        }

        List<NormalizedReview> normalized = reviews.stream()
                .map(this::normalizeOne)
                .collect(Collectors.toList());

        return new NormalizedReviewResponse("hostaway", normalized.size(), normalized);
    }

    // ======================= HTTP CALL =======================

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> callReviews(Integer limit, Integer offset) {
        String bearer = auth.getBearerToken();

        String json = wc.get()
                .uri(b -> b.path("/v1/reviews")
                        .queryParam("limit", Optional.ofNullable(limit).orElse(50))
                        .queryParam("offset", Optional.ofNullable(offset).orElse(0))
                        .build())
                .headers(h -> h.setBearerAuth(bearer))
                .retrieve()
                .bodyToMono(String.class)
                .block();

        if (json == null || json.isBlank()) return List.of();

        try {
            JsonNode root = mapper.readTree(json);
            JsonNode arr = root.isArray() ? root
                    : root.path("result").isMissingNode() ? root.path("data") : root.path("result");

            if (arr == null || !arr.isArray() || arr.size() == 0) return List.of();

            return mapper.convertValue(arr, new TypeReference<List<Map<String, Object>>>() {
            });
        } catch (IOException e) {
            return List.of();
        }
    }

    // ======================= MOCK FALLBACK =======================

    /**
     * Loads mock reviews from classpath and returns the array under result/data (or a root array).
     */
    private List<Map<String, Object>> mockReviews() throws IOException {
        try (InputStream is = HostawayReviewClient.class.getResourceAsStream(MOCK_RESOURCE)) {
            if (is == null) throw new IOException("Missing resource: " + MOCK_RESOURCE);

            JsonNode root = mapper.readTree(is);
            JsonNode arr = root.isArray() ? root
                    : root.path("result").isMissingNode() ? root.path("data") : root.path("result");

            if (arr == null || !arr.isArray()) {
                throw new IOException("Mock JSON must be an array or have 'result'/'data' array");
            }

            return mapper.convertValue(arr, new TypeReference<List<Map<String, Object>>>() {
            });
        }
    }

    // ======================= NORMALIZATION =======================

    /**
     * Maps one Hostaway review map to your NormalizedReview model.
     */
    private NormalizedReview normalizeOne(Map<String, Object> r) {
        String id = String.valueOf(r.getOrDefault("id", UUID.randomUUID().toString()));
        String direction = mapDirection((String) r.getOrDefault("type", ""));
        String status = (String) r.getOrDefault("status", "unknown");
        Integer overall = toInteger(r.get("rating"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> cats = Optional
                .ofNullable((List<Map<String, Object>>) r.get("reviewCategory"))
                .orElse(List.of());

        Map<String, Integer> categoryRatings = cats.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(
                        c -> String.valueOf(c.getOrDefault("category", "")).trim(),
                        c -> toInteger(c.get("rating")),
                        (a, b) -> b,
                        LinkedHashMap::new
                ));

        String text = (String) r.getOrDefault("publicReview", null);
        String listingName = (String) r.getOrDefault("listingName", null);
        String guestName = (String) r.getOrDefault("guestName", null);
        String channel = mapChannel(r.get("channelId"));
        OffsetDateTime submitted = parseDateTime((String) r.getOrDefault("submittedAt", null));

        return new NormalizedReview(
                id, listingName, guestName, direction, status, overall, categoryRatings, channel, text, submitted
        );
    }

    // ======================= HELPERS =======================

    private static Integer toInteger(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(String.valueOf(o));
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Accepts ISO forms and "yyyy-MM-dd HH:mm:ss" (assumes UTC if no offset).
     */
    private static OffsetDateTime parseDateTime(String s) {
        if (s == null || s.isBlank()) return null;

        // ISO_OFFSET_DATE_TIME
        try {
            return OffsetDateTime.parse(s);
        } catch (Exception ignored) {
        }

        // "yyyy-MM-ddTHH:mm:ss" without zone → assume UTC
        try {
            return OffsetDateTime.parse(s.replace(" ", "T") + "Z");
        } catch (Exception ignored) {
        }

        // Strict "yyyy-MM-dd HH:mm:ss" → assume UTC
        try {
            var f = new DateTimeFormatterBuilder()
                    .appendPattern("yyyy-MM-dd HH:mm:ss")
                    .parseDefaulting(ChronoField.OFFSET_SECONDS, 0)
                    .toFormatter(Locale.ROOT);
            LocalDateTime ldt = LocalDateTime.parse(s, f);
            return ldt.atOffset(ZoneOffset.UTC);
        } catch (Exception ignored) {
        }

        // Last resort: now UTC (or return null if you prefer)
        return OffsetDateTime.now(ZoneOffset.UTC);
    }

    private static String mapDirection(String hostawayType) {
        if (hostawayType == null) return "unknown";
        return switch (hostawayType) {
            case "host-to-guest" -> "host_to_guest";
            case "guest-to-host" -> "guest_to_host";
            default -> "unknown";
        };
    }

    private static String mapChannel(Object channelId) {
        if (channelId == null) return "hostaway";
        int id = toInt(channelId);
        return switch (id) {
            case 2001 -> "booking";
            case 2002 -> "airbnb";
            case 2003 -> "homeaway";
            case 2004 -> "expedia";
            case 2005 -> "hostaway";
            case 2012 -> "vrbo";
            default -> "unknown";
        };
    }

    private static int toInt(Object o) {
        if (o instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(String.valueOf(o));
        } catch (Exception e) {
            return -1;
        }
    }
}
