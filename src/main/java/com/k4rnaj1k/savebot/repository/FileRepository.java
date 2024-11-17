package com.k4rnaj1k.savebot.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import com.k4rnaj1k.savebot.entity.FileRef;

@Repository
public interface FileRepository extends MongoRepository<FileRef, String> {
    public FileRef getByUrl(String id);
}
