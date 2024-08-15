package com.k4rnaj1k.savebot.repository;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.k4rnaj1k.savebot.entity.InlineQueryRef;

public interface QueryRepository extends MongoRepository<InlineQueryRef, String> {
    
}
