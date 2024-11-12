package com.k4rnaj1k.savebot.model.pubbler;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class PubblerPayload {
    private String path;
    private PubblerType type;
    private String name;
    private String source;
}
