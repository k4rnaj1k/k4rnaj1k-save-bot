package com.k4rnaj1k.savebot.service;

import com.k4rnaj1k.savebot.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.objects.User;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;

    public void processUser(User user) {
        if (!userRepository.existsById(user.getId())) {
            com.k4rnaj1k.savebot.entity.User userEntity = com.k4rnaj1k.savebot.entity.User.builder()
                    .userId(user.getId())
                    .userName(user.getUserName())
                    .build();
            userRepository.save(userEntity);
        }
    }
}
