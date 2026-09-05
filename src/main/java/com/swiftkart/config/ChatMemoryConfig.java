package com.swiftkart.config;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.memory.repository.jdbc.H2ChatMemoryRepositoryDialect;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository;
import org.springframework.ai.chat.memory.repository.jdbc.PostgresChatMemoryRepositoryDialect;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class ChatMemoryConfig {

	// 	@Bean
	// 	ChatMemory chatMemory(@Value("${swiftkart.chat.memory-window:10}") int memoryWindow) {
	// 		return MessageWindowChatMemory.builder()
	// 				.chatMemoryRepository(new InMemoryChatMemoryRepository())
	// 				.maxMessages(memoryWindow)
	// 				.build();
	// 	}

    @Bean
    ChatMemory chatMemory(JdbcTemplate jdbcTemplate, @Value("${swiftkart.chat.memory-window:10}") int memoryWindow) {
        
ChatMemoryRepository chatMemoryRepository = JdbcChatMemoryRepository.builder()
    .jdbcTemplate(jdbcTemplate)
    // .dialect(new PostgresChatMemoryRepositoryDialect())
    .dialect(new H2ChatMemoryRepositoryDialect())
    .build();

ChatMemory chatMemory = MessageWindowChatMemory.builder()
    .chatMemoryRepository(chatMemoryRepository)
    .maxMessages(memoryWindow)
    .build();

    return chatMemory;


        // return MessageWindowChatMemory.builder()
        //         .chatMemoryRepository(new JdbcChatMemoryRepository(jdbcTemplate))
        //         .maxMessages(memoryWindow)
        //         .build();
    }
}