package com.k4rnaj1k.savebot.utils;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import reactor.core.publisher.Flux;

import java.io.InputStream;

@UtilityClass
@Slf4j
public class InputFileUtils {
    public InputFile getInputFromDataBuffer(Flux<DataBuffer> stream) {
        return DataBufferUtils.join(stream) // Combine DataBuffers into a single DataBuffer
                .map(dataBuffer -> {
                    InputStream combinedStream = dataBuffer.asInputStream(true); // Convert to InputStream
                    return new InputFile(combinedStream, "downloaded_video.mp4");
                })
                .block();
    }
}
