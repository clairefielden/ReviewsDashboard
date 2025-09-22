package flex.living.reviewsdashboard.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import flex.living.reviewsdashboard.config.GooglePlacesConfig;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.List;

@Component
public class GooglePlaceSearchClient {

    private final WebClient wc;
    private final GooglePlacesConfig cfg;
    private final ObjectMapper mapper = new ObjectMapper();

    // Qualify the WebClient to disambiguate
    public GooglePlaceSearchClient(
            @Qualifier("googlePlacesWebClient") WebClient wc,
            GooglePlacesConfig cfg
    ) {
        this.wc = wc;
        this.cfg = cfg;
    }

    public List<Result> findByText(String query) {
        String json = wc.get().uri(u -> u.path("/findplacefromtext/json")
                        .queryParam("input", query)
                        .queryParam("inputtype", "textquery")
                        .queryParam("fields", "place_id,name,formatted_address")
                        .queryParam("key", cfg.getApiKey())
                        .build())
                .retrieve()
                .bodyToMono(String.class)
                .block();

        List<Result> out = new ArrayList<>();
        try {
            JsonNode c = mapper.readTree(json == null ? "{}" : json).path("candidates");
            if (c.isArray()) {
                c.forEach(n -> out.add(new Result(
                        n.path("name").asText(""),
                        n.path("place_id").asText(""),
                        n.path("formatted_address").asText("")
                )));
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    public record Result(String name, String placeId, String address) {
    }
}
