package flex.living.reviewsdashboard.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import flex.living.reviewsdashboard.config.GooglePlacesConfig;
import flex.living.reviewsdashboard.model.NormalizedReview;
import flex.living.reviewsdashboard.model.NormalizedReviewResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
public class GoogleReviewClient {

    private final WebClient wc;
    private final GooglePlacesConfig cfg;
    private final ObjectMapper mapper = new ObjectMapper();

    // EXPLICIT constructor with @Qualifier to pick the right WebClient
    public GoogleReviewClient(
            @Qualifier("googlePlacesWebClient") WebClient wc,
            GooglePlacesConfig cfg
    ) {
        this.wc = wc;
        this.cfg = cfg;
    }

    /**
     * Fetch up to 5 public reviews for a Google Place ID. Cached by placeId.
     */
    @Cacheable(cacheNames = "google-reviews", key = "#placeId")
    public NormalizedReviewResponse fetchForPlace(String placeId) {
        String json = wc.get().uri(uri -> uri.path("/details/json")
                        .queryParam("place_id", placeId)
                        .queryParam("fields", "reviews,rating,user_ratings_total")
                        .queryParam("key", cfg.getApiKey())
                        .build())
                .retrieve()
                .bodyToMono(String.class)
                .block();

        List<NormalizedReview> out = new ArrayList<>();
        try {
            JsonNode root = mapper.readTree(json == null ? "{}" : json);
            JsonNode arr = root.path("result").path("reviews");
            if (arr.isArray()) for (JsonNode r : arr) out.add(mapOne(r));
        } catch (Exception ignored) {
        }
        return new NormalizedReviewResponse("google", out.size(), out);
    }

    private static NormalizedReview mapOne(JsonNode r) {
        String author = opt(r, "author_name");
        long epochSec = r.path("time").asLong(0);
        String id = "google:" + Objects.hash(author, epochSec, opt(r, "text"));

        Integer rating = r.hasNonNull("rating") ? r.get("rating").asInt() : null;
        OffsetDateTime submitted = epochSec > 0
                ? OffsetDateTime.ofInstant(Instant.ofEpochSecond(epochSec), ZoneOffset.UTC)
                : null;

        return new NormalizedReview(
                id, null, author, "guest_to_host", "published",
                rating, Map.of(), "google", opt(r, "text"), submitted
        );
    }

    private static String opt(JsonNode n, String f) {
        return n.hasNonNull(f) ? n.get(f).asText() : null;
    }

    /**
     * Find placeIds by free text (name/address). Returns top matches (name, placeId, formatted address).
     */
    public List<GooglePlaceSearchClient.Result> findByText(String query) {
        String json = wc.get().uri(u -> u.path("/findplacefromtext/json")
                        .queryParam("input", query)
                        .queryParam("inputtype", "textquery")
                        .queryParam("fields", "place_id,name,formatted_address")
                        .queryParam("key", cfg.getApiKey()).build())
                .retrieve().bodyToMono(String.class).block();

        List<GooglePlaceSearchClient.Result> out = new ArrayList<>();
        try {
            JsonNode c = mapper.readTree(json == null ? "{}" : json).path("candidates");
            if (c.isArray()) {
                for (JsonNode n : c) {
                    out.add(new GooglePlaceSearchClient.Result(
                            n.path("name").asText(""),
                            n.path("place_id").asText(""),
                            n.path("formatted_address").asText("")));
                }
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    public record Result(String name, String placeId, String address) {
    }
}
