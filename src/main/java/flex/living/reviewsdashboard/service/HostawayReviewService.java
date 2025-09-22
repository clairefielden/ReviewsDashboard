package flex.living.reviewsdashboard.service;

import flex.living.reviewsdashboard.client.HostawayReviewClient;
import flex.living.reviewsdashboard.model.NormalizedReviewResponse;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
public class HostawayReviewService {
    private final HostawayReviewClient client;

    public HostawayReviewService(HostawayReviewClient client) {
        this.client = client;
    }

    public NormalizedReviewResponse getReviews(Integer limit, Integer offset) throws IOException {
        return client.fetchAndNormalize(limit, offset);
    }
}