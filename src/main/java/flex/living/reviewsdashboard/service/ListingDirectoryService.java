package flex.living.reviewsdashboard.service;

import flex.living.reviewsdashboard.config.ListingsConfig;
import flex.living.reviewsdashboard.model.Listing;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ListingDirectoryService {
    private final ListingsConfig cfg;

    public List<Listing> all() {
        Map<String, String> map = cfg.getGooglePlaceIds();
        return map == null ? List.of() :
                map.entrySet().stream()
                        .map(e -> new Listing(e.getKey(), e.getValue(), null))
                        .toList();
    }

    /**
     * In-memory update; for persistence store this in DB later
     */
    public void put(String name, String placeId) {
        cfg.getGooglePlaceIds().put(name, placeId);
    }

    public String placeIdOf(String name) {
        return cfg.getGooglePlaceIds() == null ? null : cfg.getGooglePlaceIds().get(name);
    }
}
