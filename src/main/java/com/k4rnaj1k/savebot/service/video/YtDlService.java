package com.k4rnaj1k.savebot.service.video;

import com.k4rnaj1k.savebot.utils.ProcessUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Service
@Slf4j
public class YtDlService {

    public InputStream downloadVideo(String url) {
        return ProcessUtils.runCommand(
                "yt-dlp",
                "-f", "b[filesize<50M] / w", // Try 360p, else best available
                "--extractor-args", "youtube:getpot_bgutil_baseurl=http://bgutilprovider:8080",
                "-o", "-",   // Output to stdout
                url
        );
    }

    public String getType(String query) {
        //yt-dlp --get-filename -o "%(ext)s" <VIDEO_URL
        try (InputStream result = ProcessUtils.runCommand("yt-dlp",
                "--get-filename",
                "--extractor-args", "youtube:getpot_bgutil_baseurl=http://bgutilprovider:8080",
                "-o", "%(ext)s",
                query)) {
            return new String(result.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error(e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }
}

