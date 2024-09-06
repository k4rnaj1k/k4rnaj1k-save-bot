package com.k4rnaj1k.savebot.controller;

import java.io.File;
import java.io.IOException;
import java.io.SequenceInputStream;
import java.net.URI;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.buffer.DataBuffer;
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
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageMedia;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.ReplyParameters;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.inlinequery.ChosenInlineQuery;
import org.telegram.telegrambots.meta.api.objects.inlinequery.InlineQuery;
import org.telegram.telegrambots.meta.api.objects.inlinequery.result.cached.InlineQueryResultCachedVideo;
import org.telegram.telegrambots.meta.api.objects.media.InputMedia;
import org.telegram.telegrambots.meta.api.objects.media.InputMediaVideo;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import com.k4rnaj1k.savebot.entity.FileRef;
import com.k4rnaj1k.savebot.entity.InlineQueryRef;
import com.k4rnaj1k.savebot.entity.User;
import com.k4rnaj1k.savebot.model.CobaltResponse;
import com.k4rnaj1k.savebot.repository.FileRepository;
import com.k4rnaj1k.savebot.repository.QueryRepository;
import com.k4rnaj1k.savebot.repository.UserRepository;
import com.k4rnaj1k.savebot.service.MangaService;
import com.k4rnaj1k.savebot.service.VideoService;

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

  public SaveBotController(VideoService videoService, WebClient cobaltWebClient,
      @Value("${savebot.app.bot-token}") String botToken, UserRepository userRepository,
      FileRepository fileRepository, QueryRepository queryRepository,
      @Value("${savebot.app.placeholder-video-id}") String placeholderVideoId, MangaService mangaService) {
    this.botToken = botToken;
    telegramClient = new OkHttpTelegramClient(botToken);
    this.videoService = videoService;
    this.cobaltWebClient = cobaltWebClient;
    this.userRepository = userRepository;
    this.fileRepository = fileRepository;
    this.queryRepository = queryRepository;
    this.placeholderVideoId = placeholderVideoId;
    this.mangaService = mangaService;
  }

  @Override
  public void consume(Update update) {
    try {
      if (update.hasChosenInlineQuery()) {
        handleChosenInlineQuery(update.getChosenInlineQuery(), update);
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
      InputFile inputFile = new InputFile(fileId);
      SendVideo sendVideo = SendVideo.builder().chatId(message.getChatId()).video(inputFile).build();
      telegramClient.execute(sendVideo);
      telegramClient.execute(DeleteMessage.builder().chatId(chatId).messageId(sentMessage.getMessageId()).build());
    }
  }

  private void downloadManga(Message message) throws TelegramApiException {
    SendMessage sendMessage = SendMessage.builder().chatId(message.getChatId())
        .replyParameters(
            ReplyParameters.builder()
                .messageId(message.getMessageId()).chatId(message.getChatId()).build())
        .text("Downloading manga 📚 from given link...").build();
    Message sent = telegramClient.execute(sendMessage);
    try {
      String[] mangaFiles = mangaService.downloadFile(message.getText()).split("\n");
      for (String mangaFile : mangaFiles) {
        SendDocument sendDocument = SendDocument.builder().chatId(message.getChatId())
            .document(new InputFile(new File("result/" + mangaFile)))
            .build();
        telegramClient.execute(sendDocument);
      }
      DeleteMessage deleteMessage = DeleteMessage.builder()
          .chatId(message.getChatId()).messageId(sent.getMessageId())
          .build();
      telegramClient.execute(deleteMessage);
    } catch (IOException e) {
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
    try {
      if (fileRepository.existsById(query)) {
        return fileRepository.findById(query).orElseThrow().getFileId();
      }
      CobaltResponse cobaltResponse = videoService.getOutputStream(query);
      if (cobaltResponse.getStatus() == "error") {
        SendMessage errorMessage = SendMessage.builder()
            .chatId(chatId)
            .replyParameters(
                ReplyParameters.builder().chatId(chatId).messageId(messageId)
                    .build())
            .text("Couldn't download video from given link... Error text: %s".formatted(cobaltResponse.getText()))
            .build();
        telegramClient.execute(errorMessage);
        return "";
      }
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
      throw new TelegramApiException();
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
