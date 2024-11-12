package com.k4rnaj1k.savebot.model.pubbler;

import lombok.Data;

@Data
public class PubblerJobResponse {
    private PubblerJobStatus status;
    private PubblerPayload[] payload;
}
