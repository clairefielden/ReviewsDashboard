package flex.living.reviewsdashboard;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@EnableCaching
@SpringBootApplication
public class ReviewsDashboardApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReviewsDashboardApplication.class, args);
    }
}