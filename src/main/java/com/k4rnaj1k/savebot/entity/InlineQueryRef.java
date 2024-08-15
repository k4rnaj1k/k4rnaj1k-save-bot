package com.k4rnaj1k.savebot.entity;

import org.springframework.data.annotation.Id;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class InlineQueryRef {
    @Id
    private String id;
    private String inlineQueryId;
    private String text;
}
