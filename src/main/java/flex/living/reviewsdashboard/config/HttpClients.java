// src/main/java/flex/living/reviewsdashboard/config/HttpClients.java
package flex.living.reviewsdashboard.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Configuration
@EnableConfigurationProperties({HostawayConfig.class, GooglePlacesConfig.class})
public class HttpClients {

    @Bean
    public WebClient hostawayWebClient(HostawayConfig cfg) {
        HttpClient http = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, cfg.connectTimeoutMs())
                .responseTimeout(Duration.ofMillis(cfg.readTimeoutMs()))
                .doOnConnected(c -> c.addHandlerLast(new ReadTimeoutHandler(cfg.readTimeoutMs(), TimeUnit.MILLISECONDS)));

        return WebClient.builder()
                .baseUrl(cfg.baseUrl())
                .clientConnector(new ReactorClientHttpConnector(http))
                .build();
    }

    @Bean(name = "googlePlacesWebClient")
    public WebClient googlePlacesWebClient(GooglePlacesConfig google) {
        HttpClient http = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, google.getConnectTimeoutMs())
                .responseTimeout(Duration.ofMillis(google.getReadTimeoutMs()))
                .doOnConnected(c -> c.addHandlerLast(new ReadTimeoutHandler(google.getReadTimeoutMs(), TimeUnit.MILLISECONDS)));

        return WebClient.builder()
                .baseUrl(google.getBaseUrl())
                .clientConnector(new ReactorClientHttpConnector(http))
                .build();
    }
}
