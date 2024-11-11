package com.k4rnaj1k.savebot.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.meta.generics.TelegramClient;

@Configuration
public class SavebotConfig {

    @Bean
    public WebClient cobaltWebClient(@Value("${savebot.cobalt.api.url}") String cobaltApi) {
        return WebClient.builder().baseUrl(cobaltApi).build();
    }

    @Bean
    public WebClient pubblerWebClient() {
        return WebClient.builder().baseUrl("https://app.publer.io/").build();
    }

    @Bean
    public TelegramClient telegramClient(@Value("${savebot.app.bot-token}") String botToken) {
        return new OkHttpTelegramClient(botToken);
    }
}
