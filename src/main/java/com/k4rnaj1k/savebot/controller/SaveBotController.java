package com.k4rnaj1k.savebot.controller;

import java.io.SequenceInputStream;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.longpolling.interfaces.LongPollingUpdateConsumer;
import org.telegram.telegrambots.longpolling.starter.SpringLongPollingBot;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.methods.AnswerInlineQuery;
import org.telegram.telegrambots.meta.api.methods.send.SendVideo;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageMedia;
import org.telegram.telegrambots.meta.api.objects.InputFile;
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

import com.k4rnaj1k.savebot.entity.InlineQueryRef;
import com.k4rnaj1k.savebot.entity.User;
import com.k4rnaj1k.savebot.repository.FileRepository;
import com.k4rnaj1k.savebot.repository.QueryRepository;
import com.k4rnaj1k.savebot.repository.UserRepository;
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

    public SaveBotController(VideoService videoService, WebClient cobaltWebClient,
            @Value("${savebot.app.bot-token}") String botToken, UserRepository userRepository,
            FileRepository fileRepository, QueryRepository queryRepository, String placeholderVideoId) {
        this.botToken = botToken;
        telegramClient = new OkHttpTelegramClient(botToken);
        this.videoService = videoService;
        this.cobaltWebClient = cobaltWebClient;
        this.userRepository = userRepository;
        this.fileRepository = fileRepository;
        this.queryRepository = queryRepository;
        this.placeholderVideoId = placeholderVideoId;
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
        } catch (TelegramApiException e) {
            log.error("Telegram exception {}", e.getMessage());
        }
    }

    private void handleChosenInlineQuery(ChosenInlineQuery chosenInlineQuery, Update update)
            throws TelegramApiException {
        InlineQueryRef inlineQueryRef = queryRepository.findById(chosenInlineQuery.getResultId()).orElseThrow();
        String fileId = uploadVideo(inlineQueryRef.getText());
        log.info("{}", fileId);
        InputMedia inputMedia = new InputMediaVideo(fileId);
        log.info("{}", chosenInlineQuery);
        log.info("{}", inputMedia);
        EditMessageMedia editMessageMedia = EditMessageMedia.builder().media(inputMedia)
                .inlineMessageId(chosenInlineQuery.getInlineMessageId()).build();
        telegramClient.execute(editMessageMedia);
    }

    private void handleMessage(Message message) throws TelegramApiException {
        if (!userRepository.existsById(message.getFrom().getId())) {
            User user = User.builder()
                    .userId(message.getFrom().getId())
                    .userName(message.getFrom().getUserName())
                    .build();
            userRepository.save(user);
        }
        log.info("Handling message...");
        String fileId = uploadVideo(message.getText());
        InputFile inputFile = new InputFile(fileId);
        SendVideo sendVideo = SendVideo.builder().chatId(message.getChatId()).video(inputFile).build();
        telegramClient.execute(sendVideo);
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
                InlineKeyboardButton.builder().callbackData("Some callback data").text("Перевірити статус").build());
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
        queryRepository.save(InlineQueryRef.builder().text(inlineQuery.getQuery()).inlineQueryId(inlineQuery.getId())
                .id(resultId).build());
        AnswerInlineQuery answerInlineQuery = AnswerInlineQuery.builder()
                .inlineQueryId(inlineQuery.getId())
                .result(inlineQueryResultCachedPhoto)
                .build();

        telegramClient.execute(answerInlineQuery);
    }

    private String uploadVideo(String query) throws TelegramApiException {
        String uri = videoService.getOutputStream(query).getUrl();

        Flux<DataBuffer> videoStream = cobaltWebClient.get().uri(uri).retrieve().bodyToFlux(DataBuffer.class);
        InputFile videoFile = new InputFile(videoStream.map(b -> b.asInputStream(true))
                .reduce(SequenceInputStream::new).block(), "downloaded_video.mp4");

        SendVideo sendVideo = SendVideo.builder().chatId("-1002165579960").video(videoFile).caption(uri).build();
        String fileId = telegramClient.execute(sendVideo).getVideo().getFileId();
        return fileId;
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