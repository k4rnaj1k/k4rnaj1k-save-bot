package com.k4rnaj1k.savebot.service;

import com.k4rnaj1k.savebot.entity.FileRef;
import com.k4rnaj1k.savebot.model.CobaltResponse;
import com.k4rnaj1k.savebot.repository.FileRepository;
import com.k4rnaj1k.savebot.service.video.CobaltService;
import com.k4rnaj1k.savebot.utils.InputFileUtils;
import com.k4rnaj1k.savebot.utils.MessageUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;

@Service
@Slf4j
public class VideoService {
    private final TelegramClient telegramClient;
    private final FileRepository fileRepository;
    private final CobaltService cobaltService;
    private final String channelChatId;

    public VideoService(TelegramClient telegramClient, FileRepository fileRepository,
                        CobaltService cobaltService,
                        @Value("${savebot.app.channelChatId}") String channelChatId) {
        this.telegramClient = telegramClient;
        this.fileRepository = fileRepository;
        this.cobaltService = cobaltService;
        this.channelChatId = channelChatId;
    }

    private InputStream getInputFileFromCobalt(String query) {
        CobaltResponse cobaltResponse = cobaltService.getCobaltResponse(query);
        URI uri = cobaltResponse.getUrl();

        Flux<DataBuffer> videoStream = cobaltService.getStream(uri);

        return InputFileUtils.getInputFromDataBuffer(videoStream);
    }

//  private synchronized InputFile getInputFileFromPubler(String query) {
//    var pubblerJobId = publerService.schedulePublerJob(query);
//    // very stupid logic
//    int shouldRetryRequests = pubblerRetries;
//    while (shouldRetryRequests > 0) {
//      try {
//        var jobResponse = publerService.getPublerJobResponse(pubblerJobId);
//        if (PublerJobStatus.COMPLETE.equals(jobResponse.getStatus())) {
//          if (!StringUtils.isBlank(jobResponse.getPayload()[0].getError())) {
//            throw new RuntimeException("Error not blank %s for query %s".formatted(jobResponse.getPayload()[0].getError(), query));
//          }
//          if(Objects.isNull(jobResponse.getPayload())) {
//            throw new NoPublerPayloadException("No payload for jobId %s query %s".formatted(pubblerJobId, query));
//          }
//          if(!PublerType.VIDEO.equals(jobResponse.getPayload()[0].getType())) {
//            throw new UnsupportedOperationException();
//          }
//          String path = jobResponse.getPayload()[0].getPath();
//          Flux<DataBuffer> videoStream = publerService.getStream(path);
//
//          return InputFileUtils.getInputFromDataBuffer(videoStream);
//        } else {
//          shouldRetryRequests--;
//          Thread.sleep(Duration.ofSeconds(60 / shouldRetryRequests).toMillis());
//        }
//      } catch (NoPublerPayloadException e) {
//          //try to create another job and see if it works
//          pubblerJobId = publerService.schedulePublerJob(query);
//          shouldRetryRequests--;
//      } catch (InterruptedException e) {
//        throw new RuntimeException(e);
//      }
//    }
//    throw new RuntimeException("File not downloaded in time from publer, it seems.");
//  }

    private InputStream downloadVideo(String query) {
//    try {
        log.info("Trying to download from cobalt for query {}", query);
        return getInputFileFromCobalt(query);
//    } catch (RuntimeException e) {
//      log.info("Couldn't download \"{}\" from cobalt, falling back to publer.", query);
//      return getInputFileFromPubler(query);
//    }
    }

    public FileRef uploadVideo(String query)
            throws TelegramApiException {
        if (fileRepository.existsById(query)) {
            return fileRepository.getByUrl(query);
        }
        return uploadVideoToChannel(query);
    }

    private FileRef uploadVideoToChannel(String query) throws TelegramApiException {
        InputStream file = downloadVideo(query);

        String type = InputFileUtils.getType(file);
        InputFile inputFile = new InputFile(file, InputFileUtils.getFileName(file));
        String fileId = null;
        Message sendMediaMessage;
        if(type.startsWith("video")) {
            sendMediaMessage = telegramClient.execute(MessageUtils.sendVideo(channelChatId, inputFile));
            fileId = sendMediaMessage.getVideo().getFileId();
        }
        if(type.startsWith("image")) {
            sendMediaMessage = telegramClient.execute(MessageUtils.sendPhoto(channelChatId, inputFile));
            fileId = sendMediaMessage.getPhoto().get(0).getFileId();
        }
        try {
            inputFile.getNewMediaStream().close();
        } catch (IOException e) {
            log.error("Could not close input stream for query " + query + " file Id " + fileId);
        }
        FileRef entity = new FileRef(query, fileId, type);
        fileRepository.save(entity);
        return entity;
    }
}
