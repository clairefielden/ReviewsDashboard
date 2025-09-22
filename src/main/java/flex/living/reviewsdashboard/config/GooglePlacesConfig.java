// src/main/java/flex/living/reviewsdashboard/config/GooglePlacesConfig.java
package flex.living.reviewsdashboard.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "google.places")
public class GooglePlacesConfig {
    private String baseUrl;
    private String apiKey;
    private int connectTimeoutMs = 6000;
    private int readTimeoutMs = 10000;
}
