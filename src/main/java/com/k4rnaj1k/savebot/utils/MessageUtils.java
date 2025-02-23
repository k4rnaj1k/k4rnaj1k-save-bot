package com.k4rnaj1k.savebot.utils;

import com.k4rnaj1k.savebot.service.VideoService;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.telegram.telegrambots.meta.api.methods.send.SendDocument;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.methods.send.SendVideo;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageMedia;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.ReplyParameters;
import org.telegram.telegrambots.meta.api.objects.media.InputMedia;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@UtilityClass
public class MessageUtils {
    public static SendPhoto sendPhoto(String chatId, InputFile photo) {
        return SendPhoto.builder().chatId(chatId).photo(photo).build();
    }

    public static SendPhoto sendPhoto(Long chatId, InputFile photo) {
        return SendPhoto.builder().chatId(chatId).photo(photo).build();
    }

    public static SendVideo sendVideo(String chatId, InputFile video) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            video.getNewMediaStream().transferTo(baos);
            InputStream clone = new ByteArrayInputStream(baos.toByteArray());
            InputStream secondClone = new ByteArrayInputStream(baos.toByteArray());
            video.setMedia(secondClone, video.getMediaName());


            String widthHeight = VideoService.getAspectRatio(clone); // I'm too lazy to fix this rn. Nobody will see this anyway
            int[] size = getDimensions(widthHeight);
            int height = size[1];
            int width = size[0];

            return SendVideo.builder().chatId(chatId).video(video).width(width).height(height).build();
        } catch (IOException e) {
            log.error("Couldn't get media aspect ratio", e);
        }
        return SendVideo.builder().chatId(chatId).video(video).build();
    }

    private static int[] getDimensions(String json) throws IOException {
        // The regex looks for "width": <number> followed eventually by "height": <number>
        String regex = "\"width\"\\s*:\\s*(\\d+).*?\"height\"\\s*:\\s*(\\d+)";
        Pattern pattern = Pattern.compile(regex, Pattern.DOTALL);
        Matcher matcher = pattern.matcher(json);

        if (matcher.find()) {
            int width = Integer.parseInt(matcher.group(1));
            int height = Integer.parseInt(matcher.group(2));
            return new int[]{width, height};
        }
        throw new IOException("Couldn't parse dimensions");
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
