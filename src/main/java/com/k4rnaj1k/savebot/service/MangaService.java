package com.k4rnaj1k.savebot.service;

import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RequiredArgsConstructor
@Service
@Slf4j
public class MangaService {

  private final JmsTemplate jmsTemplate;

  public void requestMangaList(String mangaUrl) {
    jmsTemplate.convertAndSend("tome_list", mangaUrl);
  }

  public void requestDownload(String mangaUrl) {
    jmsTemplate.convertAndSend("download", mangaUrl);
  }
}
