package com.k4rnaj1k.savebot.model.publer;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class PublerPayload {
  private String error;
  private String path;
  private PublerType type;
  private String name;
  private String source;
}
