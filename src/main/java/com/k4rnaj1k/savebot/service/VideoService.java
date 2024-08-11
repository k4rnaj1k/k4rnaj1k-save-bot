package com.k4rnaj1k.savebot.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

@Service
@RequiredArgsConstructor
public class VideoService {
    private final WebClient cobaltWebClient;

    public void getOutputStream() {
        cobaltWebClient.post().body("")
    }
}
