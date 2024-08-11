package com.k4rnaj1k.savebot.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class SavebotConfig {
    @Bean
    public WebClient cobaltWebClient() {
        return WebClient.builder().baseUrl("https://api.cobalt.tools").build();
    }
}
