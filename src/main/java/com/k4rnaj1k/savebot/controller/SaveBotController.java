package com.k4rnaj1k.savebot.controller;

import java.io.File;
import java.io.IOException;
import java.io.SequenceInputStream;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException.BadRequest;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.longpolling.interfaces.LongPollingUpdateConsumer;
import org.telegram.telegrambots.longpolling.starter.SpringLongPollingBot;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.methods.AnswerInlineQuery;
import org.telegram.telegrambots.meta.api.methods.send.SendDocument;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendVideo;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.ReplyParameters;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.inlinequery.ChosenInlineQuery;
import org.telegram.telegrambots.meta.api.objects.inlinequery.InlineQuery;
import org.telegram.telegrambots.meta.api.objects.inlinequery.result.cached.InlineQueryResultCachedVideo;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.k4rnaj1k.savebot.entity.FileRef;
import com.k4rnaj1k.savebot.entity.InlineQueryRef;
import com.k4rnaj1k.savebot.entity.MangaCallback;
import com.k4rnaj1k.savebot.entity.MangaRequest;
import com.k4rnaj1k.savebot.entity.MangaRequestId;
import com.k4rnaj1k.savebot.entity.User;
import com.k4rnaj1k.savebot.model.CobaltResponse;
import com.k4rnaj1k.savebot.model.moss.ChapterData;
import com.k4rnaj1k.savebot.repository.FileRepository;
import com.k4rnaj1k.savebot.repository.MangaCallbackRepository;
import com.k4rnaj1k.savebot.repository.MangaRequestRepository;
import com.k4rnaj1k.savebot.repository.QueryRepository;
import com.k4rnaj1k.savebot.repository.UserRepository;
import com.k4rnaj1k.savebot.service.MangaService;
import com.k4rnaj1k.savebot.service.VideoService;

import jakarta.jms.JMSException;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

@Component
@Slf4j
public class SaveBotController implements SpringLongPollingBot, LongPollingSingleThreadUpdateConsumer {
  private final VideoService videoService;
  private final WebClient cobaltWebClient;
  private final TelegramClient telegramClient;
  private final UserRepository userRepository;
  private final FileRepository fileRepository;
  private final QueryRepository queryRepository;
  private final String botToken;
  private final String placeholderVideoId;
  private final MangaService mangaService;
  private final MangaRequestRepository mangaRequestRepository;
  private final MangaCallbackRepository mangaCallbackRepository;
  private final String mangaResultFolder;

  public SaveBotController(VideoService videoService, WebClient cobaltWebClient,
      @Value("${savebot.app.bot-token}") String botToken, UserRepository userRepository,
      FileRepository fileRepository, QueryRepository queryRepository,
      @Value("${savebot.app.placeholder-video-id}") String placeholderVideoId, MangaService mangaService,
      MangaRequestRepository mangaRequestRepository,
      MangaCallbackRepository mangaCallbackRepository,
      @Value("${savebot.app.manga-result-folder}") String mangaResultFolder) {
    this.botToken = botToken;
    telegramClient = new OkHttpTelegramClient(botToken);
    this.videoService = videoService;
    this.cobaltWebClient = cobaltWebClient;
    this.userRepository = userRepository;
    this.fileRepository = fileRepository;
    this.queryRepository = queryRepository;
    this.placeholderVideoId = placeholderVideoId;
    this.mangaService = mangaService;
    this.mangaRequestRepository = mangaRequestRepository;
    this.mangaCallbackRepository = mangaCallbackRepository;
    this.mangaResultFolder = mangaResultFolder;
  }

  @Override
  public void consume(Update update) {
    try {
      if (update.hasChosenInlineQuery()) {
        handleChosenInlineQuery(update.getChosenInlineQuery(), update);
      }
      if (update.hasCallbackQuery()) {
        downloadManga(update.getCallbackQuery());
        System.out.println(update.getCallbackQuery().getData());
      }
      if (update.hasInlineQuery()) {
        handleInlineQuery(update.getInlineQuery());
      } else {
        if (update.hasMessage())
          handleMessage(update.getMessage());
      }
    } catch (TelegramApiException | IOException e) {
      log.error("Telegram exception {}", e.getMessage());
    }
  }

  private void downloadManga(CallbackQuery callbackQuery) {
    Optional<MangaCallback> byId = mangaCallbackRepository.findById(UUID.fromString(callbackQuery.getData()));
    System.out.println(callbackQuery.getData());
    if (byId.isEmpty()) {
      throw new RuntimeException("Єбать, шось неочікуване.");
    }
    MangaCallback callback = byId.get();
    mangaService.requestDownload(callback.getChapterUrl());
  }

  private void handleChosenInlineQuery(ChosenInlineQuery chosenInlineQuery, Update update)
      throws TelegramApiException {
    InlineQueryRef inlineQueryRef = queryRepository.findById(chosenInlineQuery.getResultId()).orElseThrow();
    // String fileId = uploadVideo(inlineQueryRef.getText(),
    // chosenInlineQuery.getInlineMessageId());
    // InputMedia inputMedia = new InputMediaVideo(fileId);
    // EditMessageMedia editMessageMedia =
    // EditMessageMedia.builder().media(inputMedia)
    // .inlineMessageId(chosenInlineQuery.getInlineMessageId()).build();
    EditMessageText editMessageText = EditMessageText.builder().text("Inline mode is temporarily disabled.")
        .inlineMessageId(chosenInlineQuery.getInlineMessageId()).build();
    telegramClient.execute(editMessageText);
  }

  @JmsListener(destination = "tome_list_result")
  public void receiveMessage(org.apache.activemq.Message message, @Header String request) {
    // Access the fields from the JSON message
    try {
      ObjectMapper objectMapper = new ObjectMapper();
      ChapterData[] chaptersData = objectMapper.readValue(new String(message.getBody(byte[].class)),
          ChapterData[].class);
      sendManga(chaptersData, request);
    } catch (JMSException | JsonProcessingException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
  }

  @JmsListener(destination = "download_result")
  public void receiveDownloaded(org.apache.activemq.Message message, @Header String request) {
    List<MangaCallback> requestCallbacks = mangaCallbackRepository.getAllByChapterUrl(request);
    ObjectMapper objectMapper = new ObjectMapper();
    requestCallbacks.forEach(requestCallback -> {
      try {
        String downloadedChapterName = objectMapper.readValue(new String(message.getBody(byte[].class)), String.class);
        SendDocument sendDocument = SendDocument.builder().chatId(requestCallback.getChatId())
            .document(new InputFile(new File(mangaResultFolder + downloadedChapterName)))
            .build();
        telegramClient.execute(sendDocument);
      } catch (JMSException | TelegramApiException | JsonProcessingException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
    });
  }

  private void handleMessage(Message message) throws TelegramApiException, IOException {
    if (!userRepository.existsById(message.getFrom().getId())) {
      User user = User.builder()
          .userId(message.getFrom().getId())
          .userName(message.getFrom().getUserName())
          .build();
      userRepository.save(user);
    }
    log.info("Handling message...");
    if (message.getText().contains("zenko") || message.getText().contains("manga")) {
      downloadManga(message);
    } else {
      Long chatId = message.getChatId();
      Integer messageId = message.getMessageId();
      SendMessage sendMessage = SendMessage.builder()
          .chatId(message.getChatId())
          .replyParameters(ReplyParameters.builder().messageId(messageId).chatId(chatId).build())
          .text("Trying to download video from given link 📼")
          .build();
      Message sentMessage = telegramClient.execute(sendMessage);

      String fileId = uploadVideo(message.getText(), chatId, messageId);

      telegramClient.execute(DeleteMessage.builder().chatId(chatId).messageId(sentMessage.getMessageId()).build());
      if (fileId == "") {
        return;
      }
      InputFile inputFile = new InputFile(fileId);
      SendVideo sendVideo = SendVideo.builder().chatId(message.getChatId()).video(inputFile).build();
      telegramClient.execute(sendVideo);
    }
  }

  public void sendManga(ChapterData[] chapters, String request) {

    List<MangaRequest> requests = mangaRequestRepository.findAllByRequestId_request(request);
    System.out.println(requests);
    requests.forEach(mangaRequest -> {
      List<SendMessage> messagesToSend = new ArrayList<>();
      Map<String, List<ChapterData>> volumeChapters = new LinkedHashMap<>();
      for (ChapterData chapter : chapters) {
        if (volumeChapters.containsKey(chapter.getVolume())) {
          volumeChapters.get(chapter.getVolume()).add(chapter);
        } else {
          volumeChapters.put(chapter.getVolume(), new ArrayList<>(List.of(chapter)));
        }
      }
      volumeChapters.forEach((volume, volChapters) -> {
        SendMessage volMessage = new SendMessage(String.valueOf(mangaRequest.getRequestId().getChatId()), volume);
        List<InlineKeyboardRow> keyboardRows = new ArrayList<>();
        volChapters.forEach(volChapter -> {
          UUID callbackId = UUID.randomUUID();
          MangaCallback mangaCallback = new MangaCallback();
          mangaCallback.setChatId(mangaRequest.getRequestId().getChatId());
          mangaCallback.setCallbackId(callbackId);
          mangaCallback.setChapterUrl(volChapter.getChapterUrl());
          System.out.println("Saving manga callback " + callbackId.toString() + " " + volChapter.getVolume() + " "
              + volChapter.getChapter());
          mangaCallbackRepository.save(mangaCallback);
          keyboardRows.add(new InlineKeyboardRow(
              InlineKeyboardButton.builder().text(volChapter.getVolume() + " " + volChapter.getChapter())
                  .callbackData(callbackId.toString()).build()));
        });

        volMessage.setReplyMarkup(new InlineKeyboardMarkup(keyboardRows));
        messagesToSend.add(volMessage);
      });
      messagesToSend.forEach(t -> {
        try {
          telegramClient.execute(t);
        } catch (TelegramApiException e) {
          // TODO Auto-generated catch block
          e.printStackTrace();
        }
      });
    });

  }

  private void downloadManga(Message message) throws TelegramApiException {
    SendMessage sendMessage = SendMessage.builder().chatId(message.getChatId())
        .replyParameters(
            ReplyParameters.builder()
                .messageId(message.getMessageId()).chatId(message.getChatId()).build())
        .text("Downloading manga 📚 from given link...").build();
    Message sent = telegramClient.execute(sendMessage);
    try {
      // String[] mangaFiles =
      mangaService.requestMangaList(message.getText());
      MangaRequest mangaRequest = new MangaRequest();
      mangaRequest.setDate(Instant.ofEpochSecond(message.getDate()));
      mangaRequest.setRequestId(new MangaRequestId(message.getChatId(), message.getText()));
      mangaRequestRepository.save(mangaRequest);
      DeleteMessage deleteMessage = DeleteMessage.builder()
          .chatId(message.getChatId()).messageId(sent.getMessageId())
          .build();
      telegramClient.execute(deleteMessage);
    } catch (

    Exception e) {
      log.error("Exception happenned while downloading manga: {}", e.getMessage());
      SendMessage errorMessage = SendMessage.builder().chatId(message.getChatId())
          .text("Exception occurred while downloading manga...").build();
      telegramClient.execute(errorMessage);
    }
  }

  private void handleInlineQuery(InlineQuery inlineQuery) throws TelegramApiException {
    if (!userRepository.existsById(inlineQuery.getFrom().getId())) {
      User user = User.builder()
          .userId(inlineQuery.getFrom().getId())
          .userName(inlineQuery.getFrom().getUserName())
          .build();
      userRepository.save(user);
    }
    String resultId = UUID.randomUUID().toString();
    InlineKeyboardRow keyboardRow = new InlineKeyboardRow(
        InlineKeyboardButton.builder()
            .callbackData("Some callback data").text("Перевірити статус").build());
    InlineKeyboardMarkup inlineKeyboardMarkup = InlineKeyboardMarkup.builder()
        .keyboardRow(keyboardRow)
        .build();
    InlineQueryResultCachedVideo inlineQueryResultCachedPhoto = InlineQueryResultCachedVideo.builder()
        .id(resultId)
        .videoFileId(placeholderVideoId)
        .replyMarkup(inlineKeyboardMarkup)
        .title("Завантажити відео")
        .caption("Завантажую відео... А поки можете глянути тікток від ДТЕКу =)")
        .description("Завантажити відео з ютубу, інстаграму і т.д.")
        .build();
    queryRepository.save(InlineQueryRef.builder().text(inlineQuery.getQuery())
        .inlineQueryId(inlineQuery.getId())
        .id(resultId).build());
    AnswerInlineQuery answerInlineQuery = AnswerInlineQuery.builder()
        .inlineQueryId(inlineQuery.getId())
        .result(inlineQueryResultCachedPhoto)
        .build();

    telegramClient.execute(answerInlineQuery);
  }

  private String uploadVideo(String query, Long chatId, Integer messageId)
      throws TelegramApiException {
    if (fileRepository.existsById(query)) {
      return fileRepository.findById(query).orElseThrow().getFileId();
    }
    try {
      CobaltResponse cobaltResponse = videoService.getOutputStream(query);
      URI uri = cobaltResponse.getUrl();
      Flux<DataBuffer> videoStream = cobaltWebClient.get().uri(uri).retrieve().bodyToFlux(DataBuffer.class);
      InputFile videoFile = new InputFile(videoStream.map(b -> b.asInputStream(true))
          .reduce(SequenceInputStream::new).block(), "downloaded_video.mp4");

      SendVideo sendVideo = SendVideo.builder().chatId("-1002165579960").video(videoFile).caption(uri.toString())
          .build();
      String fileId = telegramClient.execute(sendVideo).getVideo().getFileId();
      fileRepository.save(FileRef.builder().url(query).fileId(fileId).build());
      return fileId;
    } catch (BadRequest e) {
      log.error(e.getResponseBodyAsString());
      SendMessage errorMessage = SendMessage.builder()
          .chatId(chatId)
          .replyParameters(
              ReplyParameters.builder().chatId(chatId).messageId(messageId)
                  .build())
          .text("Couldn't download video from given link... Error text: %s".formatted(e.getResponseBodyAsString()))
          .build();
      telegramClient.execute(errorMessage);
      return "";
    }
  }

  @Override
  public String getBotToken() {
    return botToken;
  }

  @Override
  public LongPollingUpdateConsumer getUpdatesConsumer() {
    return this;
  }

}
