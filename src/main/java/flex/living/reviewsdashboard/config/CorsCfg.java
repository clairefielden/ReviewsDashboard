package flex.living.reviewsdashboard.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
public class CorsCfg {

    /**
     * Define CORS rules for all endpoints.
     */
    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration cfg = new CorsConfiguration();

        // Allow local dev + your Vercel frontend
        cfg.setAllowedOriginPatterns(List.of(
                "http://localhost:5173",
                "http://localhost:3000",
                "https://*.vercel.app"   // matches all preview + production URLs
        ));

        // Allow standard HTTP methods
        cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));

        // Allow all headers (Authorization, Content-Type, etc.)
        cfg.setAllowedHeaders(List.of("*"));

        // Allow cookies/credentials if needed
        cfg.setAllowCredentials(true);

        // Apply to all paths
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cfg);

        return source;
    }

    /**
     * Make sure Spring Security actually uses the CORS configuration.
     */
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(Customizer.withDefaults())  // enable CORS with above config
                .csrf(csrf -> csrf.disable())     // disable CSRF if not needed
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/health", "/actuator/**").permitAll()
                        .anyRequest().permitAll()     // loosen for now; tighten if you add auth
                );

        return http.build();
    }
}
