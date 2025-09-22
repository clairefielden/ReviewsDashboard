package flex.living.reviewsdashboard.api;


import flex.living.reviewsdashboard.client.GooglePlaceSearchClient;
import flex.living.reviewsdashboard.model.Listing;
import flex.living.reviewsdashboard.service.ListingDirectoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/listings")
@RequiredArgsConstructor
public class ListingController {

    private final ListingDirectoryService directory;
    private final GooglePlaceSearchClient search;

    /**
     * Dropdown data
     */
    @GetMapping
    public List<Listing> all() {
        return directory.all();
    }

    /**
     * Search Google → show choices to user
     */
    @GetMapping("/search")
    public List<GooglePlaceSearchClient.Result> search(@RequestParam String q) {
        return search.findByText(q);
    }

    /**
     * Save (in-memory). Later: persist to DB
     */
    @PostMapping
    public ResponseEntity<Void> add(@RequestParam String name, @RequestParam String placeId) {
        directory.put(name, placeId);
        return ResponseEntity.noContent().build();
    }
}
