package com.k4rnaj1k.savebot.entity;

import java.util.UUID;

import org.springframework.data.annotation.Id;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MangaCallback {
  @Id
  private UUID callbackId;
  private String chapterUrl;
  private Long chatId;
}
