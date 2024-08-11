package com.k4rnaj1k.savebot.controller;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.nio.channels.Channels;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.longpolling.interfaces.LongPollingUpdateConsumer;
import org.telegram.telegrambots.longpolling.starter.SpringLongPollingBot;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.methods.AnswerInlineQuery;
import org.telegram.telegrambots.meta.api.methods.send.SendVideo;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.inlinequery.InlineQuery;
import org.telegram.telegrambots.meta.api.objects.inlinequery.result.cached.InlineQueryResultCachedVideo;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import com.k4rnaj1k.savebot.service.VideoService;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class SaveBotController implements SpringLongPollingBot, LongPollingSingleThreadUpdateConsumer {
    private final VideoService videoService;
    private final WebClient cobaltWebClient;
    private final TelegramClient telegramClient;
    private final String botToken;

    public SaveBotController(VideoService videoService, WebClient cobaltWebClient,
            @Value("${savebot.app.bot-token}") String botToken) {
        this.botToken = botToken;
        telegramClient = new OkHttpTelegramClient(botToken);
        this.videoService = videoService;
        this.cobaltWebClient = cobaltWebClient;
    }

    @Override
    public void consume(Update update) {
        System.out.println(update);
        if (update.hasInlineQuery()) {
            handleInlineQuery(update.getInlineQuery());
        }
    }

    private void handleInlineQuery(InlineQuery inlineQuery) {
        try {
            String videoFileId = uploadVideo(inlineQuery.getQuery());

            InlineQueryResultCachedVideo resultVideo = InlineQueryResultCachedVideo.builder()
                    .id(inlineQuery.getId())
                    .videoFileId(videoFileId)
                    .title("downloaded-video")
                    .build();

            AnswerInlineQuery answerInlineQuery = AnswerInlineQuery.builder()
                    .inlineQueryId(inlineQuery.getId())
                    .result(resultVideo)
                    .build();

            telegramClient.execute(answerInlineQuery);
        } catch (TelegramApiException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    private String uploadVideo(String query) throws TelegramApiException {
        String uri = videoService.getOutputStream(query).getUrl();

        Flux<DataBuffer> videoStream = cobaltWebClient.get().uri(uri).retrieve().bodyToFlux(DataBuffer.class);
        InputFile videoFile = new InputFile(videoStream.map(b -> b.asInputStream(true))
        .reduce(SequenceInputStream::new).block(), "downloaded_video.mp4");

        SendVideo sendVideo = SendVideo.builder().chatId("-1002165579960").video(videoFile).caption(uri).build();
        // sendVideo.setChatId("");
        // sendVideo.setVideo(videoFile);
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