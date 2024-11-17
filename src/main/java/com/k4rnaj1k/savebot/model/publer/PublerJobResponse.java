package com.k4rnaj1k.savebot.model.publer;

import lombok.Data;

@Data
public class PublerJobResponse {
    private PublerJobStatus status;
    private PublerPayload[] payload;
}
