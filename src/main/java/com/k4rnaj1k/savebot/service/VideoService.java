package com.k4rnaj1k.savebot.service;

import com.k4rnaj1k.savebot.entity.FileRef;
import com.k4rnaj1k.savebot.model.pubbler.*;
import com.k4rnaj1k.savebot.repository.FileRepository;
import lombok.extern.slf4j.Slf4j;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import com.k4rnaj1k.savebot.model.CobaltRequest;
import com.k4rnaj1k.savebot.model.CobaltResponse;

import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendVideo;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.ReplyParameters;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.InputStream;
import java.io.SequenceInputStream;
import java.net.URI;
import java.time.Duration;
import java.util.Objects;

@Service
@Slf4j
public class VideoService {
  private final WebClient cobaltWebClient;
  private final TelegramClient telegramClient;
  private final FileRepository fileRepository;
  private final WebClient pubblerWebClient;
  private final Integer pubblerRetries;
  private final String channelChatId;

  public VideoService(WebClient cobaltWebClient, TelegramClient telegramClient, FileRepository fileRepository,
      WebClient pubblerWebClient,
      @Value("${savebot.app.pubblerRetries}") Integer pubblerRetries,
      @Value("${savebot.app.channelChatId}") String channelChatId) {
    this.cobaltWebClient = cobaltWebClient;
    this.telegramClient = telegramClient;
    this.fileRepository = fileRepository;
    this.pubblerWebClient = pubblerWebClient;
    this.pubblerRetries = pubblerRetries;
    this.channelChatId = channelChatId;
  }

  private CobaltResponse getSteamFromCobalt(String uri) {
    CobaltRequest cobaltRequest = CobaltRequest.builder()
        .url(uri)
        .build();

    Mono<CobaltRequest> requestMono = Mono.just(cobaltRequest);
    ResponseEntity<CobaltResponse> cobaltResponseEntity = cobaltWebClient
        .post().uri("/")
        .contentType(MediaType.APPLICATION_JSON).body(requestMono, CobaltRequest.class)
        .accept(MediaType.APPLICATION_JSON)
        .retrieve()
        .toEntity(CobaltResponse.class).block();
    if (Objects.isNull(cobaltResponseEntity) || !cobaltResponseEntity.hasBody()) {
      throw new RuntimeException("No response for query from cobalt.");
    }
    return cobaltResponseEntity.getBody();
  }

  private InputFile getInputFileFromCobalt(String query) {
    CobaltResponse cobaltResponse = getSteamFromCobalt(query);
    URI uri = cobaltResponse.getUrl();
    Flux<DataBuffer> videoStream = cobaltWebClient.get().uri(uri).retrieve().bodyToFlux(DataBuffer.class);
    return DataBufferUtils.join(videoStream) // Combine DataBuffers into a single DataBuffer
        .map(dataBuffer -> {
          InputStream combinedStream = dataBuffer.asInputStream(true); // Convert to InputStream
          return new InputFile(combinedStream, "downloaded_video.mp4");
        })
        .block();
  }

  private synchronized InputFile getInputFileFromPubbler(String query) {
    var pubblerRequest = new PubblerRequest(query);
    ResponseEntity<PubblerJobCreatedResponse> pubblerJobCreatedEntity = null;
    try {
      pubblerJobCreatedEntity = pubblerWebClient.post().uri("/hooks/media")
          .header("Referer", "https://publer.io/")
          .contentType(MediaType.APPLICATION_JSON).body(Mono.just(pubblerRequest), PubblerRequest.class)
          .accept(MediaType.APPLICATION_JSON)
          .retrieve()
          .toEntity(PubblerJobCreatedResponse.class).block();
    } catch (WebClientResponseException e) {
      System.out.println(e.getResponseBodyAsString());
    }
    if (Objects.isNull(pubblerJobCreatedEntity) || !pubblerJobCreatedEntity.hasBody()) {
      log.error("Error response from pubbler for query: {}", query);
      throw new RuntimeException("Response from another service has errors.");
    }

    var pubblerJobCreatedResponse = pubblerJobCreatedEntity.getBody();
    if (Strings.isNotBlank(pubblerJobCreatedResponse.getError())) {
      log.error("Response with error from pubbler for query: {}, error: {}", query,
          pubblerJobCreatedResponse.getError());
    }
    var pubblerJobId = pubblerJobCreatedResponse.getJobId();
    // very stupid logic
    int shouldRetryRequests = pubblerRetries;
    while (shouldRetryRequests > 0) {
      try {
        ResponseEntity<PubblerJobResponse> pubblerResponseEntity = pubblerWebClient
            .get().uri("/api/v1/job_status/%s".formatted(pubblerJobId))
            .retrieve().toEntity(PubblerJobResponse.class).block();
        if (Objects.isNull(pubblerResponseEntity) || !pubblerResponseEntity.hasBody()) {
          throw new RuntimeException("Pubbler response without a body.");
        }
        PubblerJobResponse body = pubblerResponseEntity.getBody();
        if (PubblerJobStatus.COMPLETE.equals(body.getStatus())) {
          if (!StringUtils.isBlank(body.getPayload()[0].getError())) {
            throw new RuntimeException("Error not blank");
          }
          String path = body.getPayload()[0].getPath();
          Flux<DataBuffer> videoStream = pubblerWebClient.get().uri(path).retrieve().bodyToFlux(DataBuffer.class);

          return new InputFile(videoStream.map(b -> b.asInputStream(true))
              .reduce(SequenceInputStream::new).block(), "downloaded_video.mp4");
        } else {
          shouldRetryRequests--;
          Thread.sleep(Duration.ofSeconds(60 / shouldRetryRequests).toMillis());
        }
      } catch (RuntimeException e) {
        log.error(e.getMessage());
      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
    }
    throw new RuntimeException("File not downloaded in time from pubbler, it seems.");
  }

  private InputFile downloadVideo(String query) {
    try {
      return getInputFileFromCobalt(query);
    } catch (RuntimeException e) {
      return getInputFileFromPubbler(query);
    }
  }

  public String uploadVideo(String query, Long chatId, Integer messageId)
      throws TelegramApiException {
    if (fileRepository.existsById(query)) {
      return fileRepository.findById(query).orElseThrow().getFileId();
    }
    try {
      return uploadVideoToChannel(query);
    } catch (WebClientResponseException.BadRequest e) {
      log.error(e.getResponseBodyAsString());
      SendMessage errorMessage = SendMessage.builder()
          .chatId(chatId)
          .replyParameters(
              ReplyParameters.builder().chatId(chatId).messageId(messageId)
                  .build())
          .text("Couldn't download video from given link... Error text: %s".formatted(e.getResponseBodyAsString()))
          .build();
      telegramClient.execute(errorMessage);
      return "";
    }
  }

  public String uploadVideo(String query)
      throws TelegramApiException {
    if (fileRepository.existsById(query)) {
      return fileRepository.findById(query).orElseThrow().getFileId();
    }
    try {
      return uploadVideoToChannel(query);
    } catch (WebClientResponseException.BadRequest e) {
      return "";
    }
  }

  private String uploadVideoToChannel(String query) throws TelegramApiException {
    InputFile videoFile = downloadVideo(query);
    SendVideo sendVideo = SendVideo.builder().chatId(channelChatId).video(videoFile)
        .build();
    String fileId = telegramClient.execute(sendVideo).getVideo().getFileId();
    fileRepository.save(FileRef.builder().url(query).fileId(fileId).build());
    return fileId;
  }
}
