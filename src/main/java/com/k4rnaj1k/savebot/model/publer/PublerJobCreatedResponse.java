package com.k4rnaj1k.savebot.model.publer;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class PublerJobCreatedResponse {
    @JsonProperty("job_id")
    private String jobId;
    private String error;
}
