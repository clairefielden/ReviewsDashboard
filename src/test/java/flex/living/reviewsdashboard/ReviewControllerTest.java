package flex.living.reviewsdashboard;

import flex.living.reviewsdashboard.api.ReviewController;
import flex.living.reviewsdashboard.model.NormalizedReview;
import flex.living.reviewsdashboard.model.NormalizedReviewResponse;
import flex.living.reviewsdashboard.service.HostawayReviewService;
import flex.living.reviewsdashboard.service.ReviewsAggregationService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReviewController.class)
class ReviewControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private HostawayReviewService hostawayService;

    @MockBean
    private ReviewsAggregationService aggregationService;

    @Test
    void hostaway_returnsNormalizedPayload() throws Exception {
        var review = new NormalizedReview(
                "7453",
                "2B N1 A - 29 Shoreditch Heights",
                "Shane Finkelstein",
                "host_to_guest",
                "published",
                null,
                Map.of("cleanliness", 10, "communication", 10, "respect_house_rules", 10),
                "hostaway",
                "Shane and family are wonderful! Would definitely host again :)",
                OffsetDateTime.parse("2020-08-21T22:45:14Z")
        );
        var resp = new NormalizedReviewResponse("hostaway", 1, List.of(review));

        Mockito.when(hostawayService.getReviews(1, 0)).thenReturn(resp);

        mockMvc.perform(get("/api/reviews/hostaway")
                        .param("limit", "1")
                        .param("offset", "0")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("hostaway"))
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.reviews[0].id").value("7453"))
                .andExpect(jsonPath("$.reviews[0].direction").value("host_to_guest"))
                .andExpect(jsonPath("$.reviews[0].categoryRatings.cleanliness").value(10))
                .andExpect(jsonPath("$.reviews[0].submittedAt").value("2020-08-21T22:45:14Z"));
    }

    @Test
    void hostaway_onException_returnsEmptyPayload() throws Exception {
        Mockito.when(hostawayService.getReviews(anyInt(), anyInt()))
                .thenThrow(new RuntimeException("boom"));

        mockMvc.perform(get("/api/reviews/hostaway")
                        .param("limit", "5")
                        .param("offset", "0")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("hostaway"))
                .andExpect(jsonPath("$.count").value(0))
                .andExpect(jsonPath("$.reviews").isArray())
                .andExpect(jsonPath("$.reviews.length()").value(0));
    }

    @Test
    void combined_returnsMergedPayload() throws Exception {
        var r1 = new NormalizedReview(
                "hostaway:1",
                "Listing A",
                "Alice",
                "guest_to_host",
                "published",
                9,
                Map.of(),
                "hostaway",
                "Lovely stay!",
                OffsetDateTime.parse("2024-01-10T12:00:00Z")
        );
        var r2 = new NormalizedReview(
                "google:2",
                "Listing B",
                "Bob",
                "guest_to_host",
                "published",
                5,
                Map.of(),
                "google",
                "Okay experience.",
                OffsetDateTime.parse("2024-01-11T09:00:00Z")
        );
        var combined = new NormalizedReviewResponse("combined", 2, List.of(r1, r2));

        Mockito.when(aggregationService.combined(500, 0)).thenReturn(combined);

        mockMvc.perform(get("/api/reviews/combined")
                        .param("limit", "500")
                        .param("offset", "0")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("combined"))
                .andExpect(jsonPath("$.count").value(2))
                .andExpect(jsonPath("$.reviews[0].channel").exists())
                .andExpect(jsonPath("$.reviews[1].channel").exists());
    }
}
