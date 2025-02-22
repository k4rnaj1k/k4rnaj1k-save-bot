package com.k4rnaj1k.savebot.utils;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.apache.tika.mime.MimeType;
import org.apache.tika.mime.MimeTypeException;
import org.apache.tika.mime.MimeTypes;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.io.InputStream;

@UtilityClass
@Slf4j
public class InputFileUtils {

    private static String mapMimeTypeToExtension(String detectedType) {

        // Get the default MIME types registry
        MimeTypes allTypes = MimeTypes.getDefaultMimeTypes();
        // Retrieve the MimeType object for the detected type
        try {
            MimeType mimeType = allTypes.forName(detectedType);
            System.out.println(mimeType.getName());
            // Get the extension (including the dot, e.g., ".pdf")
            return mimeType.getExtension();
        } catch (MimeTypeException e) {
            throw new RuntimeException(e);
        }
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

    public static String getFileName(InputStream inputStream, String query) {
        String detect = getType(inputStream);
        return "downloaded" + mapMimeTypeToExtension(detect);
    }

    public InputStream getInputFromDataBuffer(Flux<DataBuffer> stream) {
        return DataBufferUtils.join(stream) // Combine DataBuffers into a single DataBuffer
                .map(dataBuffer -> dataBuffer.asInputStream(true)) // Convert to InputStream
                .block();
    }
}
