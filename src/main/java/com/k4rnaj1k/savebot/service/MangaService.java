package com.k4rnaj1k.savebot.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.k4rnaj1k.savebot.entity.MangaCallback;
import com.k4rnaj1k.savebot.entity.MangaRequest;
import com.k4rnaj1k.savebot.entity.MangaRequestId;
import com.k4rnaj1k.savebot.model.moss.ChapterData;
import com.k4rnaj1k.savebot.repository.MangaCallbackRepository;
import com.k4rnaj1k.savebot.repository.MangaRequestRepository;
import jakarta.jms.JMSException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.SendDocument;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.ReplyParameters;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.io.File;
import java.time.Instant;
import java.util.*;

@Service
@Slf4j
public class MangaService {

    private final JmsTemplate jmsTemplate;
    private final TelegramClient telegramClient;
    private final MangaCallbackRepository mangaCallbackRepository;
    private final MangaRequestRepository mangaRequestRepository;
    private final String mangaResultFolder;

    public MangaService(JmsTemplate jmsTemplate, TelegramClient telegramClient, MangaCallbackRepository mangaCallbackRepository, MangaRequestRepository mangaRequestRepository, @Value("${savebot.app.manga-result-folder}") String mangaResultFolder) {
        this.jmsTemplate = jmsTemplate;
        this.telegramClient = telegramClient;
        this.mangaCallbackRepository = mangaCallbackRepository;
        this.mangaRequestRepository = mangaRequestRepository;
        this.mangaResultFolder = mangaResultFolder;
    }

    public void requestMangaList(String mangaUrl) {
        jmsTemplate.convertAndSend("tome_list", mangaUrl);
    }

    @JmsListener(destination = "tome_list_result")
    public void receiveMessage(org.apache.activemq.Message message, @Header String request) {
        // Access the fields from the JSON message
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            ChapterData[] chaptersData = objectMapper.readValue(new String(message.getBody(byte[].class)), ChapterData[].class);
            sendManga(chaptersData, request);
        } catch (JMSException | JsonProcessingException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }


    public void sendManga(ChapterData[] chapters, String request) {

        List<MangaRequest> requests = mangaRequestRepository.findAllByRequestId_request(request);
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
                SendMessage volMessage = new SendMessage(String.valueOf(mangaRequest.getRequestId().getChatId()), "Том " + volume);
                List<InlineKeyboardRow> keyboardRows = new ArrayList<>();
                volChapters.forEach(volChapter -> {
                    UUID callbackId = UUID.randomUUID();
                    MangaCallback mangaCallback = new MangaCallback();
                    mangaCallback.setChatId(mangaRequest.getRequestId().getChatId());
                    mangaCallback.setCallbackId(callbackId);
                    mangaCallback.setChapterUrl(volChapter.getChapterUrl());
                    mangaCallbackRepository.save(mangaCallback);
                    keyboardRows.add(new InlineKeyboardRow(
                            InlineKeyboardButton.builder().text("Розділ " + volChapter.getChapter())
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

    public void requestDownload(String mangaUrl) {
        jmsTemplate.convertAndSend("download", mangaUrl);
    }

    public void downloadManga(Message message) throws TelegramApiException {
        SendMessage sendMessage = SendMessage.builder().chatId(message.getChatId()).replyParameters(ReplyParameters.builder().messageId(message.getMessageId()).chatId(message.getChatId()).build()).text("Downloading manga 📚 from given link...").build();
        Message sent = telegramClient.execute(sendMessage);
        try {
            // String[] mangaFiles =
            requestMangaList(message.getText());
            MangaRequest mangaRequest = new MangaRequest();
            mangaRequest.setDate(Instant.ofEpochSecond(message.getDate()));
            mangaRequest.setRequestId(new MangaRequestId(message.getChatId(), message.getText()));
            mangaRequestRepository.save(mangaRequest);
            DeleteMessage deleteMessage = DeleteMessage.builder().chatId(message.getChatId()).messageId(sent.getMessageId()).build();
            telegramClient.execute(deleteMessage);
        } catch (Exception e) {
            log.error("Exception happened while downloading manga: {}", e.getMessage());
            SendMessage errorMessage = SendMessage.builder().chatId(message.getChatId()).text("Exception occurred while downloading manga...").build();
            telegramClient.execute(errorMessage);
        }
    }


    private void sendMangaDownloadResult(Long chatId, String downloadedChapterName) {
        SendDocument sendDocument = SendDocument.builder().chatId(chatId)
                .document(new InputFile(new File(mangaResultFolder + downloadedChapterName))).build();
        try {
            telegramClient.execute(sendDocument);
        } catch (TelegramApiException e) {
            throw new RuntimeException(e);
        }
    }

    @JmsListener(destination = "download_result")
    public void receiveDownloaded(org.apache.activemq.Message message, @Header String request) {
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            String downloadedChapterName = objectMapper.readValue(new String(message.getBody(byte[].class)), String.class);
            List<MangaCallback> requestCallbacks = mangaCallbackRepository.getAllByChapterUrl(request);
            if(requestCallbacks.isEmpty()) {
                List<MangaRequest> requests = mangaRequestRepository.findAllByRequestId_request(request);
                requests.forEach(mangaRequest -> sendMangaDownloadResult(mangaRequest.getRequestId().getChatId(), downloadedChapterName));
            }
            requestCallbacks.forEach(requestCallback -> sendMangaDownloadResult(requestCallback.getChatId(), downloadedChapterName));
        } catch (JsonProcessingException | JMSException e) {
            throw new RuntimeException(e);
        }

    }
}
