package com.k4rnaj1k.savebot.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.k4rnaj1k.savebot.entity.MangaCallback;

public interface MangaCallbackRepository extends MongoRepository<MangaCallback, UUID> {
  List<MangaCallback> getAllByChapterUrl(String chapterUrl);

}
