package com.k4rnaj1k.savebot.entity;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MangaRequestId {
  private Long chatId;
  private String request;
}
