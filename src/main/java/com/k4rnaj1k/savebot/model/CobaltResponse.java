package com.k4rnaj1k.savebot.model;

import java.net.URI;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CobaltResponse {
    private URI url;
    private String status;
    private String text;
    private String fileName;
}
