package flex.living.reviewsdashboard.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Configuration
@ConfigurationProperties(prefix = "listings")
@Getter
@Setter
public class ListingsConfig {
    private Map<String, String> googlePlaceIds; // listingName -> placeId
}
