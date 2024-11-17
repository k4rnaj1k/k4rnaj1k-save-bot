package com.k4rnaj1k.savebot.entity;

import java.time.Instant;

import org.springframework.data.annotation.Id;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MangaRequest {
  @Id
  private MangaRequestId requestId;
  private Integer messageId;
  private Instant date;
}
