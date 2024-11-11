package com.k4rnaj1k.savebot.model.pubbler;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class PubblerJobCreatedResponse {
    @JsonProperty("job_id")
    private String jobId;
    private String error;
}
