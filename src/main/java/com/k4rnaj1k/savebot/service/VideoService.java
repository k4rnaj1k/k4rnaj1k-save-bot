package com.k4rnaj1k.savebot.service;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import com.k4rnaj1k.savebot.model.CobaltRequest;
import com.k4rnaj1k.savebot.model.CobaltResponse;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class VideoService {
  private final WebClient cobaltWebClient;

  public CobaltResponse getOutputStream(String uri) {
    CobaltRequest cobaltRequest = CobaltRequest.builder()
        .url(uri)
        .build();

    Mono<CobaltRequest> requestMono = Mono.just(cobaltRequest);
    CobaltResponse response = cobaltWebClient
        .post().uri("/api/json")
        .contentType(MediaType.APPLICATION_JSON).body(requestMono, CobaltRequest.class)
        .accept(MediaType.APPLICATION_JSON)
        .retrieve()
        .toEntity(CobaltResponse.class).block().getBody();
    return response;
  }

  public void downloadAndSendVideo(String url) {

  }
}
