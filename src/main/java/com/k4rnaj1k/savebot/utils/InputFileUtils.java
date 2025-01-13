package com.k4rnaj1k.savebot.utils;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.io.InputStream;

@UtilityClass
@Slf4j
public class InputFileUtils {

    private static String mapMimeTypeToExtension(String detectedType) {
        if (detectedType.startsWith("image"))
            return detectedType.replace("image/", ".");
        if (detectedType.startsWith("video"))
            return detectedType.replace("video/", ".");
        throw new UnsupportedOperationException("Unknown mime type");
    }

    public static String getType(InputStream inputStream) {
        Tika tika = new Tika();
        try {
            return tika.detect(inputStream);
        } catch (IOException e) {
            log.error("Error getting type " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    public static String getFileName(InputStream inputStream) {
        String detect = getType(inputStream);
        return "downloaded" + mapMimeTypeToExtension(detect);
    }

    public InputStream getInputFromDataBuffer(Flux<DataBuffer> stream) {
        return DataBufferUtils.join(stream) // Combine DataBuffers into a single DataBuffer
                .map(dataBuffer -> dataBuffer.asInputStream(true)) // Convert to InputStream
                .block();
    }
}
