package com.k4rnaj1k.savebot.service.video;

import com.k4rnaj1k.savebot.model.CobaltRequest;
import com.k4rnaj1k.savebot.model.CobaltResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class CobaltService {
    private final WebClient cobaltWebClient;

    public CobaltResponse getCobaltResponse(String uri) {
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

    public Flux<DataBuffer> getStream(URI url) {
        return cobaltWebClient.get().uri(url).retrieve().bodyToFlux(DataBuffer.class);
    }
}
