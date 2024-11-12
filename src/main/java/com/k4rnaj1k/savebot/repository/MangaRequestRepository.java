package com.k4rnaj1k.savebot.repository;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.k4rnaj1k.savebot.entity.MangaRequest;
import com.k4rnaj1k.savebot.entity.MangaRequestId;

public interface MangaRequestRepository extends MongoRepository<MangaRequest, MangaRequestId> {

  List<MangaRequest> findAllByRequestId_request(String request);

}
