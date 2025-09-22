package flex.living.reviewsdashboard.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "hostaway")
public record HostawayConfig(
        String baseUrl,
        Integer accountId,
        String clientSecret,
        Integer connectTimeoutMs,
        Integer readTimeoutMs
) {
}