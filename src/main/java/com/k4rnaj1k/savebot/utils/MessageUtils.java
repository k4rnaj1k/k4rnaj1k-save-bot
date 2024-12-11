package com.k4rnaj1k.savebot.utils;

import lombok.experimental.UtilityClass;
import org.telegram.telegrambots.meta.api.methods.send.SendDocument;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendVideo;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageMedia;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.ReplyParameters;
import org.telegram.telegrambots.meta.api.objects.media.InputMedia;

@UtilityClass
public class MessageUtils {
    public static SendVideo sendVideo(String chatId, InputFile video) {
        return SendVideo.builder().chatId(chatId).video(video).build();
    }

    public static SendVideo sendVideo(Long chatId, InputFile video) {
        return SendVideo.builder().chatId(chatId).video(video).build();
    }

    public static EditMessageMedia editMessageMedia(InputMedia media, String inlineMessageId) {
        return EditMessageMedia.builder().media(media)
                .inlineMessageId(inlineMessageId).build();
    }

    public static DeleteMessage deleteMessage(Long chatId, Integer messageId) {
        return DeleteMessage.builder().chatId(chatId).messageId(messageId).build();
    }

    public static SendDocument sendManga(String fileName, Long chatId) {
        InputFile inputFile = new InputFile(fileName);
        return SendDocument.builder().document(inputFile).chatId(chatId).build();
    }

    public static SendMessage sendMessage(Long chatId, String messageText) {
        return SendMessage.builder().chatId(chatId).text(messageText).build();
    }

    public static SendMessage sendReply(Long chatId, String messageText, Integer replyToMessage) {
        return SendMessage.builder().chatId(chatId).text(messageText).replyParameters(ReplyParameters.builder().chatId(chatId).messageId(replyToMessage).build()).build();
    }
}
