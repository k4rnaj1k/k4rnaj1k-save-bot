package com.k4rnaj1k.savebot.handler;

import com.k4rnaj1k.savebot.entity.FileRef;
import com.k4rnaj1k.savebot.service.MangaService;
import com.k4rnaj1k.savebot.service.UserService;
import com.k4rnaj1k.savebot.service.VideoService;
import com.k4rnaj1k.savebot.utils.MessageUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.ReplyParameters;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.io.IOException;
import java.util.Objects;

import static org.telegram.telegrambots.meta.api.methods.ParseMode.MARKDOWNV2;

@Slf4j
@RequiredArgsConstructor
@Service
public class MessageHandler {

    private final MangaService mangaService;
    private final TelegramClient telegramClient;
    private final VideoService videoService;
    private final UserService userService;

    public void handleMessage(Message message) throws TelegramApiException, IOException {
        userService.processUser(message.getFrom());
        //TODO: add logging
        if (message.getText().contains("zenko") || message.getText().contains("manga")) {
            mangaService.downloadManga(message);
        } else {
            if (message.getText().equals("/start")) {
                handleStartCommand(message);
            }
            String regex = "^(https?|ftp|file)://[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|]";
            if (!message.getText().matches(regex)) {
                return;
            }
            handleMediaRequest(message);

        }
    }

    private void handleMediaRequest(Message message) throws TelegramApiException {
        Long chatId = message.getChatId();
        Integer messageId = message.getMessageId();
        Message sentMessage = null;
        if (!message.isGroupMessage()) {
            SendMessage sendMessage = SendMessage.builder()
                    .chatId(message.getChatId())
                    .replyParameters(ReplyParameters.builder().messageId(messageId).chatId(chatId).build())
                    .text("Намагаюсь завантажити відео за посиланням 📼")
                    .build();
            sentMessage = telegramClient.execute(sendMessage);
        }
        uploadAndSendVideo(message, sentMessage);
    }

    private void handleStartCommand(Message message) throws TelegramApiException {
        telegramClient.execute(SendMessage.builder()
                .text("""
                        Вітаю, це бот для завантаження відео та манги за посиланнями.
                        Підтримуються багато сервісів з відео. Завантаження фото буде в майбутньому.
                        Головна фішка бота - підтримка інлайн режиму, тому працює у будь-якому чаті)
                        ||напиши назву бота + посилання на відео в будь-якому чаті і повідомлення буде наіслане з відео||
                        """)
                .chatId(message.getChatId())
                .parseMode(MARKDOWNV2)
                .build());
    }

    private void uploadAndSendVideo(Message message, Message sentMessage) throws TelegramApiException {
        Long chatId = message.getChatId();
        FileRef file = null;
        try {
            file = videoService.uploadVideo(message.getText());
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            if (!message.isGroupMessage()) {
                SendMessage sendMessage = MessageUtils.sendReply(chatId, "Сталася помилка при спробі завантажити відео", message.getMessageId());
                telegramClient.execute(sendMessage);
            }
        }
        if (Objects.nonNull(sentMessage))
            telegramClient.execute(MessageUtils.deleteMessage(chatId, sentMessage.getMessageId()));

        if (file == null) {
            return;
        }
        InputFile inputFile = new InputFile(file.getFileId());
        if (file.getMimeType() != null && file.getMimeType().startsWith("image")) {
            telegramClient.execute(MessageUtils.sendPhoto(chatId, inputFile));
        } else {
            telegramClient.execute(MessageUtils.sendVideo(message.getChatId(), inputFile));
        }
    }
}
