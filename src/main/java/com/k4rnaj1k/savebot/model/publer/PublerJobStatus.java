package com.k4rnaj1k.savebot.model.publer;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum PublerJobStatus {
    @JsonProperty("complete")
    COMPLETE,
    @JsonProperty("working")
    WORKING
}
