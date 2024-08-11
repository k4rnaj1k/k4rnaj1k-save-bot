package com.k4rnaj1k.savebot.entity;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;

@Getter
@Setter
@Builder
public class User {

    @Id
    private String chatId;

    private String username;
}
