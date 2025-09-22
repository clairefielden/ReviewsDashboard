package flex.living.reviewsdashboard;

import flex.living.reviewsdashboard.client.HostawayAuthClient;
import flex.living.reviewsdashboard.client.HostawayReviewClient;
import flex.living.reviewsdashboard.config.HostawayConfig;
import flex.living.reviewsdashboard.model.NormalizedReviewResponse;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

class HostawayReviewClientTest {

    private MockWebServer server;
    private HostawayAuthClient auth;
    private HostawayReviewClient client;


    @BeforeEach
    void setup() throws Exception {
        server = new MockWebServer();
        server.start();
        var base = server.url("/").toString();
        var cfg = new HostawayConfig(
                base.substring(0, base.length() - 1),
                61148,
                "test-secret",
                5000,
                5000
        );
        WebClient wc = WebClient.builder().baseUrl(cfg.baseUrl()).build();
        auth = new HostawayAuthClient(wc, cfg);
        client = new HostawayReviewClient(wc, auth);
    }

    @AfterEach
    void teardown() throws Exception {
        server.shutdown();
    }

    @Test
    void fetchAndNormalize_success() throws IOException {
        // 1) token
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("{\"access_token\":\"tok\",\"expires_in\":3600}")
                .addHeader("Content-Type", "application/json"));

        String body = loadResource("src/main/resources/mock-reviews.json");

        // 2) reviews
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody(body)
                .addHeader("Content-Type", "application/json"));

        NormalizedReviewResponse r = client.fetchAndNormalize(50, 0);

        assertThat(r.source()).isEqualTo("hostaway");
        assertThat(r.count()).isEqualTo(1);
        assertThat(r.reviews()).hasSize(1);
        var rev = r.reviews().get(0);
        assertThat(rev.id()).isEqualTo("7453");
        assertThat(rev.direction()).isEqualTo("host_to_guest");
        assertThat(rev.categoryRatings().get("cleanliness")).isEqualTo(10);
        assertThat(rev.channel()).isEqualTo("hostaway"); // channelId 2005 maps to hostaway
    }

    @Test
    void fetchAndNormalize_403_thenRefreshTokenAndRetry() throws IOException {
        // Initial token
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("{\"access_token\":\"tok1\",\"expires_in\":3600}")
                .addHeader("Content-Type", "application/json"));

        // First reviews call returns 403
        server.enqueue(new MockResponse()
                .setResponseCode(403)
                .setBody("{\"status\":\"fail\",\"message\":\"denied\"}")
                .addHeader("Content-Type", "application/json"));

        // Refresh token call
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("{\"access_token\":\"tok2\",\"expires_in\":3600}")
                .addHeader("Content-Type", "application/json"));

        // Retry reviews now succeeds
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("""
                        {"status":"success","result":[{"id":1,"type":"guest-to-host","status":"published","submittedAt":"2024-01-01 10:00:00","guestName":"A","listingName":"L"}]}
                        """)
                .addHeader("Content-Type", "application/json"));

        var r = client.fetchAndNormalize(10, 0);
        assertThat(r.count()).isEqualTo(1);
        assertThat(r.reviews().get(0).direction()).isEqualTo("guest_to_host");
        // Requests sequence: token, reviews(403), token(refresh), reviews(200)
        assertThat(server.getRequestCount()).isEqualTo(4);
    }

    @Test
    void fetchAndNormalize_emptyResult_fallsBackToMock() throws IOException {
        // token
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("{\"access_token\":\"tok\",\"expires_in\":3600}")
                .addHeader("Content-Type", "application/json"));

        // reviews returns empty list
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("{\"status\":\"success\",\"result\":[]}")
                .addHeader("Content-Type", "application/json"));

        var r = client.fetchAndNormalize(10, 0);
        // our client injects a mock review when result is empty
        assertThat(r.count()).isEqualTo(1);
        assertThat(r.reviews().get(0).guestName()).isEqualTo("Shane Finkelstein");
    }

    private String loadResource(String resourcePath) throws IOException {
        // Load from src/test/resources
        return Files.readString(Path.of(resourcePath), UTF_8);
    }
}
