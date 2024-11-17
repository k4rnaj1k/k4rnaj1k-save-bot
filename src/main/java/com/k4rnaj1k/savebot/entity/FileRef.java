package com.k4rnaj1k.savebot.entity;

import lombok.*;
import org.springframework.data.annotation.Id;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class FileRef {
    @Id
    private String url;
    private String fileId;
}
