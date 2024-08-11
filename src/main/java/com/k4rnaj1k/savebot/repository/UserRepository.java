package com.k4rnaj1k.savebot.repository;

import com.k4rnaj1k.savebot.entity.User;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

// @Repository
public interface UserRepository extends MongoRepository<User, String> {
}
