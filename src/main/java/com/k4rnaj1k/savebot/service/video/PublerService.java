package com.k4rnaj1k.savebot.service.video;

import com.k4rnaj1k.savebot.exception.PublerJobErrorResponse;
import com.k4rnaj1k.savebot.exception.PublerJobNull;
import com.k4rnaj1k.savebot.model.publer.PublerJobCreatedResponse;
import com.k4rnaj1k.savebot.model.publer.PublerJobResponse;
import com.k4rnaj1k.savebot.model.publer.PublerRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Objects;

@Slf4j
@RequiredArgsConstructor
@Service
public class PublerService {
    private final WebClient pubblerWebClient;

    public String schedulePublerJob(String query) {
            var publerRequest = new PublerRequest(query);

            var pubblerJobCreatedEntity = pubblerWebClient.post().uri("/hooks/media")
                    .header("Referer", "https://publer.io/")
                    .contentType(MediaType.APPLICATION_JSON).body(Mono.just(publerRequest), PublerRequest.class)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .toEntity(PublerJobCreatedResponse.class).block();


        if (Objects.isNull(pubblerJobCreatedEntity) || !pubblerJobCreatedEntity.hasBody()) {
            log.error("Error response from publer for query: {}", query);
            throw new RuntimeException("Response from another service has errors.");
        }
        var publerJobCreatedResponse = pubblerJobCreatedEntity.getBody();
        if(Objects.isNull(publerJobCreatedResponse)) {
            throw new PublerJobNull();
        }
        if (Strings.isNotBlank(publerJobCreatedResponse.getError())) {
            throw new PublerJobErrorResponse(publerJobCreatedResponse.getError());
        }

        return publerJobCreatedResponse.getJobId();
    }

    public PublerJobResponse getPublerJobResponse(String jobId) {
        ResponseEntity<PublerJobResponse> pubblerResponseEntity = pubblerWebClient
                .get().uri("/api/v1/job_status/" + jobId)
                .retrieve().toEntity(PublerJobResponse.class).block();
        if (Objects.isNull(pubblerResponseEntity) || !pubblerResponseEntity.hasBody()) {
            throw new RuntimeException("Publer response without a body.");
        }

        return pubblerResponseEntity.getBody();
    }

    public Flux<DataBuffer> getStream(String uri) {
        return pubblerWebClient.get().uri(uri).retrieve().bodyToFlux(DataBuffer.class);
    }
}
