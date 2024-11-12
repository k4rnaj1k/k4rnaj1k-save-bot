package com.k4rnaj1k.savebot.model.pubbler;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum PubblerJobStatus {
    @JsonProperty("complete")
    COMPLETE,
    @JsonProperty("working")
    WORKING
}
