package com.k4rnaj1k.savebot.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
@Slf4j
public class MangaService {

  public String downloadFile(String mangaUrl) throws IOException {
    String pythonPath = "python";
    if (StringUtils.isNotBlank(System.getenv("PYTHON_PATH"))) {
      pythonPath = System.getenv("PYTHON_PATH");
    }
    ProcessBuilder processBuilder = new ProcessBuilder(pythonPath, "scripts/get_tome.py",
        "\"%s\"".formatted(mangaUrl));
    processBuilder.redirectErrorStream(true);
    Process process = processBuilder.start();
    String processOutput = new String(process.getInputStream().readAllBytes());
    if (!processOutput.split("\n")[0].endsWith(".pdf")) {
      log.warn("unexpected process output: {}", processOutput);
      throw new IOException("Something happenned...");
    }
    return processOutput;
  }

}
