package flex.living.reviewsdashboard.client;

import flex.living.reviewsdashboard.config.HostawayConfig;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class HostawayAuthClient {
    private final WebClient wc;
    private final HostawayConfig cfg;

    // naive in-memory token cache
    private final AtomicReference<String> tokenRef = new AtomicReference<>();
    private final AtomicReference<Instant> expiresAt = new AtomicReference<>(Instant.EPOCH);

    public HostawayAuthClient(WebClient hostawayWebClient, HostawayConfig cfg) {
        this.wc = hostawayWebClient;
        this.cfg = cfg;
    }

    public String getBearerToken() {
        // refresh if missing or “stale” (Hostaway tokens last up to 24 months; we still refresh on 403 elsewhere)
        if (Instant.now().isAfter(expiresAt.get())) {
            refreshToken();
        }
        return tokenRef.get();
    }

    public synchronized void refreshToken() {
        var form = "grant_type=client_credentials"
                + "&client_id=" + cfg.accountId()
                + "&client_secret=" + cfg.clientSecret()
                + "&scope=general";

        Map<?, ?> body = wc.post()
                .uri("/v1/accessTokens")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .bodyValue(form)
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        Object tokenObj = body.get("access_token");
        String token = tokenObj != null ? tokenObj.toString() : "";

        Object expiresInObj = body.get("expires_in");
        long seconds;
        if (expiresInObj instanceof Number n) {
            seconds = n.longValue();
        } else if (expiresInObj != null) {
            try {
                seconds = Long.parseLong(expiresInObj.toString());
            } catch (NumberFormatException e) {
                seconds = 60L * 60L * 24L * 30L; // default: 30 days
            }
        } else {
            seconds = 60L * 60L * 24L * 30L; // default: 30 days
        }

        tokenRef.set(token);
        expiresAt.set(Instant.now().plusSeconds(seconds - 60));
    }
}