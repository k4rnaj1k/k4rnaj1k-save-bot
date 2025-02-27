package com.k4rnaj1k.savebot.controller;

import com.k4rnaj1k.savebot.entity.FileRef;
import com.k4rnaj1k.savebot.entity.InlineQueryRef;
import com.k4rnaj1k.savebot.entity.MangaCallback;
import com.k4rnaj1k.savebot.entity.User;
import com.k4rnaj1k.savebot.handler.MessageHandler;
import com.k4rnaj1k.savebot.repository.MangaCallbackRepository;
import com.k4rnaj1k.savebot.repository.QueryRepository;
import com.k4rnaj1k.savebot.repository.UserRepository;
import com.k4rnaj1k.savebot.service.MangaService;
import com.k4rnaj1k.savebot.service.VideoService;
import com.k4rnaj1k.savebot.utils.MessageUtils;
import com.k4rnaj1k.savebot.utils.UrlUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.telegram.telegrambots.longpolling.interfaces.LongPollingUpdateConsumer;
import org.telegram.telegrambots.longpolling.starter.SpringLongPollingBot;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.methods.AnswerInlineQuery;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.inlinequery.ChosenInlineQuery;
import org.telegram.telegrambots.meta.api.objects.inlinequery.InlineQuery;
import org.telegram.telegrambots.meta.api.objects.inlinequery.inputmessagecontent.InputMessageContent;
import org.telegram.telegrambots.meta.api.objects.inlinequery.inputmessagecontent.InputTextMessageContent;
import org.telegram.telegrambots.meta.api.objects.inlinequery.result.InlineQueryResultArticle;
import org.telegram.telegrambots.meta.api.objects.media.InputMedia;
import org.telegram.telegrambots.meta.api.objects.media.InputMediaPhoto;
import org.telegram.telegrambots.meta.api.objects.media.InputMediaVideo;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

import static org.telegram.telegrambots.meta.api.methods.ParseMode.MARKDOWNV2;

@Component
@Slf4j
public class SaveBotController implements SpringLongPollingBot, LongPollingSingleThreadUpdateConsumer {
    private final VideoService videoService;
    private final TelegramClient telegramClient;
    private final UserRepository userRepository;
    private final QueryRepository queryRepository;
    private final String botToken;
    private final MangaService mangaService;
    private final MangaCallbackRepository mangaCallbackRepository;
    private final MessageHandler messageHandler;

    public SaveBotController(VideoService videoService,
                             @Value("${savebot.app.bot-token}") String botToken, UserRepository userRepository,
                             QueryRepository queryRepository, MangaService mangaService,
                             MangaCallbackRepository mangaCallbackRepository,
                             TelegramClient telegramClient,
                             MessageHandler messageHandler) {
        this.botToken = botToken;
        this.telegramClient = telegramClient;
        this.videoService = videoService;
        this.userRepository = userRepository;
        this.queryRepository = queryRepository;
        this.mangaService = mangaService;
        this.mangaCallbackRepository = mangaCallbackRepository;
        this.messageHandler = messageHandler;
    }

    @Override
    public void consume(Update update) {
        try {
            if (update.hasChosenInlineQuery()) {
                handleChosenInlineQuery(update);
            }
            if (update.hasCallbackQuery()) {
                downloadManga(update.getCallbackQuery());
            }
            if (update.hasInlineQuery()) {
                handleInlineQuery(update.getInlineQuery());
            } else if (update.hasMessage()) {
                messageHandler.handleMessage(update.getMessage());
            }
        } catch (TelegramApiException | IOException e) {
            log.error("Telegram exception {}", e.getMessage());
        }
    }

    private void downloadManga(CallbackQuery callbackQuery) {
        Optional<MangaCallback> byId = mangaCallbackRepository.findById(UUID.fromString(callbackQuery.getData()));
        if (byId.isEmpty()) {
            throw new RuntimeException("Єбать, шось неочікуване.");
        }
        MangaCallback callback = byId.get();
        mangaService.requestDownload(callback.getChapterUrl());
    }

    private void handleChosenInlineQuery(Update update)
            throws TelegramApiException {
        ChosenInlineQuery chosenInlineQuery = update.getChosenInlineQuery();
        InlineQueryRef inlineQueryRef = queryRepository.findById(chosenInlineQuery.getResultId()).orElseThrow();
        try {
            FileRef file = videoService.uploadVideo(inlineQueryRef.getText());

            String fileId = file.getFileId();
            InputMedia inputMedia = file.getMimeType() != null && file.getMimeType().startsWith("image") ? new InputMediaPhoto(fileId) : new InputMediaVideo(fileId);

            telegramClient.execute(MessageUtils.editMessageMedia(inputMedia, chosenInlineQuery.getInlineMessageId()));
        } catch (WebClientResponseException.BadRequest e) {
            log.error(e.getResponseBodyAsString());
            EditMessageText editMessageText = EditMessageText.builder().inlineMessageId(update.getChosenInlineQuery().getInlineMessageId())
                    .text("Сталася помилка при спробі завантажити файл за посиланням:\n" + inlineQueryRef.getText())
                    .build();

            telegramClient.execute(editMessageText);
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
        if (inlineQuery.getQuery().isBlank()) {
            return;
        }
        if (!UrlUtils.isUrl(inlineQuery.getQuery())) {
            return;
        }
        String resultId = UUID.randomUUID().toString();
        InlineKeyboardRow keyboardRow = new InlineKeyboardRow(
                InlineKeyboardButton.builder()
                        .callbackData("Some callback data").text("Перевірити статус").build());
        InlineKeyboardMarkup inlineKeyboardMarkup = InlineKeyboardMarkup.builder()
                .keyboardRow(keyboardRow)
                .build();
        InputMessageContent inputTextMessageContent = InputTextMessageContent.builder()
                .messageText("Завантажую відео за [посиланням](" + inlineQuery.getQuery() + ")")
                .parseMode(MARKDOWNV2)
                .build();
        InlineQueryResultArticle inlineQueryResultArticle = InlineQueryResultArticle.builder()
                .id(resultId)
                .replyMarkup(inlineKeyboardMarkup)
                .inputMessageContent(inputTextMessageContent)
                .title("Завантажити фото/відео з ютубу, інстаграму і т.д.")
                .build();

        queryRepository.save(InlineQueryRef.builder().text(inlineQuery.getQuery())
                .inlineQueryId(inlineQuery.getId())
                .id(resultId).build());
        AnswerInlineQuery answerInlineQuery = AnswerInlineQuery.builder()
                .inlineQueryId(inlineQuery.getId())
                .result(inlineQueryResultArticle)
                .build();

        telegramClient.execute(answerInlineQuery);
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
