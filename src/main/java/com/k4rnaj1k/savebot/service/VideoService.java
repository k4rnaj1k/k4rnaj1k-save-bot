package com.k4rnaj1k.savebot.service;

import com.k4rnaj1k.savebot.entity.FileRef;
import com.k4rnaj1k.savebot.model.CobaltResponse;
import com.k4rnaj1k.savebot.repository.FileRepository;
import com.k4rnaj1k.savebot.service.video.CobaltService;
import com.k4rnaj1k.savebot.service.video.YtDlService;
import com.k4rnaj1k.savebot.utils.InputFileUtils;
import com.k4rnaj1k.savebot.utils.MessageUtils;
import com.k4rnaj1k.savebot.utils.ProcessUtils;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

@Service
@Slf4j
public class VideoService {
    private final TelegramClient telegramClient;
    private final FileRepository fileRepository;
    private final CobaltService cobaltService;
    private final String channelChatId;
    private final YtDlService ytDlService;

    public VideoService(TelegramClient telegramClient, FileRepository fileRepository,
                        CobaltService cobaltService,
                        @Value("${savebot.app.channelChatId}") String channelChatId,
                        YtDlService ytDlService) {
        this.telegramClient = telegramClient;
        this.fileRepository = fileRepository;
        this.cobaltService = cobaltService;
        this.channelChatId = channelChatId;
        this.ytDlService = ytDlService;
    }

    private InputStream getInputFileFromCobalt(String query) {
        CobaltResponse cobaltResponse = cobaltService.getCobaltResponse(query);
        URI uri = cobaltResponse.getUrl();

        Flux<DataBuffer> videoStream = cobaltService.getStream(uri);

        return InputFileUtils.getInputFromDataBuffer(videoStream);
    }

    private InputStream getInputFileFromYtDl(String query) throws IOException, InterruptedException {
        InputStream inputStream = ytDlService.downloadVideo(query);
        if (ytDlService.getType(query).contains("webm")) {
            inputStream = VideoService.convert(inputStream);
        }
        return inputStream;
    }

    private InputStream downloadVideo(String query) {
        try {
            log.info("Trying to download from ytdl for query {}", query);
            return getInputFileFromYtDl(query);
        } catch (RuntimeException | IOException | InterruptedException e) {
            log.error(e.getMessage(), e);
            log.info("Couldn't download \"{}\" from ytdl, falling back to cobalt.", query);
            return getInputFileFromCobalt(query);
        }
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
        String fileName = InputFileUtils.getFileName(file, query);
        if (fileName.contains(".qt")) {
            try {
                file = addPreview(file);
            } catch (IOException e) {
                log.error(e.getMessage(), e);
            }
        }

        InputFile inputFile = new InputFile(file, fileName);

        String fileId;
        Message sendMediaMessage;
        if (type.startsWith("image")) {
            sendMediaMessage = telegramClient.execute(MessageUtils.sendPhoto(channelChatId, inputFile));
            fileId = sendMediaMessage.getPhoto().get(0).getFileId();
        } else {
            sendMediaMessage = telegramClient.execute(MessageUtils.sendVideo(channelChatId, inputFile));
            fileId = sendMediaMessage.getVideo().getFileId();
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

    public static InputStream addPreview(InputStream inputStream) throws IOException {
        Path inputFile = Files.createTempFile("input", "");
        Files.copy(inputStream, inputFile, StandardCopyOption.REPLACE_EXISTING);

        InputStream result = ProcessUtils.runCommand(
                "ffmpeg",
                "-i", inputFile.toString(),               // Main video input
                "-ss", "1",             // Seek position for thumbnail extraction
                "-i", inputFile.toString(),               // Use the same file for thumbnail extraction
                "-frames:v", "1",              // Extract one frame from the second input
                "-q:v", "2",                   // Set quality for the extracted frame
                "-map", "0",                   // Map all streams from the first input (main video/audio)
                "-map", "1",                   // Map the extracted frame from the second input
                "-c", "copy",                  // Copy the main video/audio streams without re-encoding
                "-c:v:1", "mjpeg",             // Re-encode the cover art stream to MJPEG
                "-metadata:s:v:1", "mimetype=image/jpeg",
                "-f", "mp4",
                "-movflags", "frag_keyframe+empty_moov",
                "-disposition:v:1", "attached_pic",  // Mark the second video stream as attached cover art
                "-"                     // Output file with embedded preview thumbnail
        );
        Files.deleteIfExists(inputFile);
        inputStream.close();
        return result;
    }

    public static InputStream convert(InputStream input) throws IOException, InterruptedException {
        Path webmTempFile = Files.createTempFile("input", ".webm");
        try {
            // Write the WebM InputStream to the temporary file
            Files.copy(input, webmTempFile, StandardCopyOption.REPLACE_EXISTING);
            // Create the FFmpeg process
            InputStream result = ProcessUtils.runCommand(
                    "ffmpeg",
                    "-i", webmTempFile.toString(), // Read input from the temporary file
                    "-c:v", "libx264",             // Video codec
                    "-c:a", "aac",                // Audio codec
                    "-f", "mp4",                  // Output format
                    "-movflags", "frag_keyframe+empty_moov",
                    "-"                      // Write output to stdout (pipe)
            );
            Files.deleteIfExists(webmTempFile);

            return result;
        } catch (IOException e) {
            // Ensure the temporary file is deleted if an error occurs
            Files.deleteIfExists(webmTempFile);
            throw e;
        }
    }
}
