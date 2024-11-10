package com.k4rnaj1k.savebot.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class SavebotConfig {

  @Bean
  public WebClient cobaltWebClient(@Value("${savebot.cobalt.api.url}") String cobaltApi) {
    return WebClient.builder().baseUrl(cobaltApi).build();
  }
}
