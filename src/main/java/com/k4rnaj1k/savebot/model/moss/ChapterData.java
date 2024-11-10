package com.k4rnaj1k.savebot.model.moss;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
public class ChapterData {

  private String volume;
  @JsonProperty("chapter_id")
  private String chapterId;
  @JsonProperty("chapter_url")
  private String chapterUrl;
  private String chapter;
}
