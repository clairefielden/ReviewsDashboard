package flex.living.reviewsdashboard;

import flex.living.reviewsdashboard.client.HostawayAuthClient;
import flex.living.reviewsdashboard.config.HostawayConfig;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import static org.assertj.core.api.Assertions.assertThat;

class HostawayAuthClientTest {

    private MockWebServer server;
    private HostawayAuthClient auth;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();

        var baseUrl = server.url("/").toString();
        var cfg = new HostawayConfig(
                baseUrl.substring(0, baseUrl.length() - 1), // remove trailing slash
                61148,
                "test-secret",
                5000,
                5000
        );
        WebClient wc = WebClient.builder().baseUrl(cfg.baseUrl()).build();

        auth = new HostawayAuthClient(wc, cfg);
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    @Test
    void getBearerToken_fetchesAndCachesToken() {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("{\"access_token\":\"abc123\",\"expires_in\":3600}")
                .addHeader("Content-Type", "application/json"));

        String token = auth.getBearerToken();
        assertThat(token).isEqualTo("abc123");

        // No second enqueue: ensure cached token is returned without hitting server again.
        String token2 = auth.getBearerToken();
        assertThat(token2).isEqualTo("abc123");
        assertThat(server.getRequestCount()).isEqualTo(1);
    }

    @Test
    void refreshToken_handlesStringExpiresInAndMissingFields() {
        // expires_in as STRING
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("{\"access_token\":\"tok-1\",\"expires_in\":\"1800\"}")
                .addHeader("Content-Type", "application/json"));

        auth.refreshToken();
        assertThat(auth.getBearerToken()).isEqualTo("tok-1");

        // missing expires_in → defaults to 30 days
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("{\"access_token\":\"tok-2\"}")
                .addHeader("Content-Type", "application/json"));

        auth.refreshToken();
        assertThat(auth.getBearerToken()).isEqualTo("tok-2");
    }
}
