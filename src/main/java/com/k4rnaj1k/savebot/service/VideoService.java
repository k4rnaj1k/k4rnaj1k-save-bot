package com.k4rnaj1k.savebot.service;

import com.k4rnaj1k.savebot.entity.FileRef;
import com.k4rnaj1k.savebot.exception.NoPublerPayloadException;
import com.k4rnaj1k.savebot.model.CobaltResponse;
import com.k4rnaj1k.savebot.model.publer.PublerJobStatus;
import com.k4rnaj1k.savebot.model.publer.PublerType;
import com.k4rnaj1k.savebot.repository.FileRepository;
import com.k4rnaj1k.savebot.service.video.CobaltService;
import com.k4rnaj1k.savebot.service.video.PublerService;
import com.k4rnaj1k.savebot.utils.InputFileUtils;
import com.k4rnaj1k.savebot.utils.MessageUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.Objects;

@Service
@Slf4j
public class VideoService {
  private final TelegramClient telegramClient;
  private final FileRepository fileRepository;
  private final PublerService publerService;
  private final CobaltService cobaltService;
  private final Integer pubblerRetries;
  private final String channelChatId;

  public VideoService(TelegramClient telegramClient, FileRepository fileRepository,
      PublerService publerService,
      CobaltService cobaltService,
      @Value("${savebot.app.pubblerRetries}") Integer pubblerRetries,
      @Value("${savebot.app.channelChatId}") String channelChatId) {
    this.telegramClient = telegramClient;
    this.fileRepository = fileRepository;
    this.publerService = publerService;
    this.cobaltService = cobaltService;
    this.pubblerRetries = pubblerRetries;
    this.channelChatId = channelChatId;
  }

  private InputFile getInputFileFromCobalt(String query) {
    CobaltResponse cobaltResponse = cobaltService.getCobaltResponse(query);
    URI uri = cobaltResponse.getUrl();

    Flux<DataBuffer> videoStream = cobaltService.getStream(uri);

    return InputFileUtils.getInputFromDataBuffer(videoStream);
  }

  private synchronized InputFile getInputFileFromPubler(String query) {
    var pubblerJobId = publerService.schedulePublerJob(query);
    // very stupid logic
    int shouldRetryRequests = pubblerRetries;
    while (shouldRetryRequests > 0) {
      try {
        var jobResponse = publerService.getPublerJobResponse(pubblerJobId);
        if (PublerJobStatus.COMPLETE.equals(jobResponse.getStatus())) {
          if (!StringUtils.isBlank(jobResponse.getPayload()[0].getError())) {
            throw new RuntimeException("Error not blank %s for query %s".formatted(jobResponse.getPayload()[0].getError(), query));
          }
          if(Objects.isNull(jobResponse.getPayload())) {
            throw new NoPublerPayloadException("No payload for jobId %s query %s".formatted(pubblerJobId, query));
          }
          if(!PublerType.VIDEO.equals(jobResponse.getPayload()[0].getType())) {
            throw new UnsupportedOperationException();
          }
          String path = jobResponse.getPayload()[0].getPath();
          Flux<DataBuffer> videoStream = publerService.getStream(path);

          return InputFileUtils.getInputFromDataBuffer(videoStream);
        } else {
          shouldRetryRequests--;
          Thread.sleep(Duration.ofSeconds(60 / shouldRetryRequests).toMillis());
        }
      } catch (NoPublerPayloadException e) {
          //try to create another job and see if it works
          pubblerJobId = publerService.schedulePublerJob(query);
          shouldRetryRequests--;
      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
    }
    throw new RuntimeException("File not downloaded in time from publer, it seems.");
  }

  private InputFile downloadVideo(String query) {
    try {
      return getInputFileFromCobalt(query);
    } catch (RuntimeException e) {
      return getInputFileFromPubler(query);
    }
  }

  public String uploadVideo(String query)
          throws TelegramApiException {
    if (fileRepository.existsById(query)) {
      return fileRepository.getByUrl(query).getFileId();
    }
    return uploadVideoToChannel(query);
  }

  private String uploadVideoToChannel(String query) throws TelegramApiException {
    InputFile videoFile = downloadVideo(query);

    Message channelVideoMessage = telegramClient.execute(MessageUtils.sendVideo(channelChatId, videoFile));
      try {
          videoFile.getNewMediaStream().close();
      } catch (IOException e) {
        log.error("Could not close input stream for query " + query  + " file Id " + channelVideoMessage.getVideo().getFileId());
      }
      String fileId = channelVideoMessage.getVideo().getFileId();
    fileRepository.save(new FileRef(query, fileId));
    return fileId;
  }
}
