package com.k4rnaj1k.savebot.entity;

import org.springframework.data.annotation.Id;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class FileRef {
    @Id
    private String url;
    private String fileId;
}
