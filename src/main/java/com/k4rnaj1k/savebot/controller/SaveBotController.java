package com.k4rnaj1k.savebot.controller;

import com.k4rnaj1k.savebot.entity.InlineQueryRef;
import com.k4rnaj1k.savebot.entity.MangaCallback;
import com.k4rnaj1k.savebot.entity.User;
import com.k4rnaj1k.savebot.repository.MangaCallbackRepository;
import com.k4rnaj1k.savebot.repository.QueryRepository;
import com.k4rnaj1k.savebot.repository.UserRepository;
import com.k4rnaj1k.savebot.service.MangaService;
import com.k4rnaj1k.savebot.service.VideoService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.longpolling.interfaces.LongPollingUpdateConsumer;
import org.telegram.telegrambots.longpolling.starter.SpringLongPollingBot;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.methods.AnswerInlineQuery;
import org.telegram.telegrambots.meta.api.methods.ParseMode;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendVideo;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageMedia;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.ReplyParameters;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.inlinequery.ChosenInlineQuery;
import org.telegram.telegrambots.meta.api.objects.inlinequery.InlineQuery;
import org.telegram.telegrambots.meta.api.objects.inlinequery.inputmessagecontent.InputMessageContent;
import org.telegram.telegrambots.meta.api.objects.inlinequery.inputmessagecontent.InputTextMessageContent;
import org.telegram.telegrambots.meta.api.objects.inlinequery.result.InlineQueryResultArticle;
import org.telegram.telegrambots.meta.api.objects.media.InputMedia;
import org.telegram.telegrambots.meta.api.objects.media.InputMediaVideo;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.io.IOException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

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

    public SaveBotController(VideoService videoService,
                             @Value("${savebot.app.bot-token}") String botToken, UserRepository userRepository,
                             QueryRepository queryRepository, MangaService mangaService,
                             MangaCallbackRepository mangaCallbackRepository,
                             TelegramClient telegramClient) {
        this.botToken = botToken;
        this.telegramClient = telegramClient;
        this.videoService = videoService;
        this.userRepository = userRepository;
        this.queryRepository = queryRepository;
        this.mangaService = mangaService;
        this.mangaCallbackRepository = mangaCallbackRepository;
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
        String fileId = videoService.uploadVideo(inlineQueryRef.getText());
        InputMedia inputMedia = new InputMediaVideo(fileId);
        EditMessageMedia editMessageMedia =
                EditMessageMedia.builder().media(inputMedia)
                        .inlineMessageId(chosenInlineQuery.getInlineMessageId()).build();
//        EditMessageText editMessageText = EditMessageText.builder().text("Inline mode is temporarily disabled.")
//                .inlineMessageId(chosenInlineQuery.getInlineMessageId()).build();
        telegramClient.execute(editMessageMedia);
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
            mangaService.downloadManga(message);
        } else {
            Long chatId = message.getChatId();
            Integer messageId = message.getMessageId();
            SendMessage sendMessage = SendMessage.builder()
                    .chatId(message.getChatId())
                    .replyParameters(ReplyParameters.builder().messageId(messageId).chatId(chatId).build())
                    .text("Trying to download video from given link 📼")
                    .build();
            Message sentMessage = telegramClient.execute(sendMessage);

            String fileId = videoService.uploadVideo(message.getText(), chatId, messageId);

            telegramClient.execute(DeleteMessage.builder().chatId(chatId).messageId(sentMessage.getMessageId()).build());
            if (Objects.equals(fileId, "")) {
                return;
            }
            InputFile inputFile = new InputFile(fileId);
            SendVideo sendVideo = SendVideo.builder().chatId(message.getChatId()).video(inputFile).build();
            telegramClient.execute(sendVideo);
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
        InputMessageContent inputTextMessageContent = InputTextMessageContent.builder()
                .messageText("Завантажую відео за [посиланням](" + inlineQuery.getQuery() + ")")
                .parseMode(ParseMode.MARKDOWNV2)
                .build();
        InlineQueryResultArticle inlineQueryResultArticle = InlineQueryResultArticle.builder()
                .id(resultId)
                .replyMarkup(inlineKeyboardMarkup)
                .inputMessageContent(inputTextMessageContent)
                .title("Завантажити відео з ютубу, інстаграму і т.д.")
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
