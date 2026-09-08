package com.swiftkart.controller;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.http.ProblemDetail;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
// import org.springframework.ai.chat.client.advisor.ChatMemoryAdvisor;
// import org.springframework.ai.chat.memory.ChatMemory;

import java.util.UUID;

@RestController 
@RequestMapping("/api/chat")
public class ChatController {

	private final ChatClient chatClient;
	private final MessageChatMemoryAdvisor memoryAdvisor;

	public ChatController(ChatClient chatClient, ChatMemory chatMemory ) {
		this.chatClient = chatClient;
		this.memoryAdvisor = MessageChatMemoryAdvisor.builder(chatMemory).build();
	}

	@PostMapping
	public ChatResponse chat(@RequestBody ChatRequest request) {
		if (request.userId() == null || request.userId().isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "userId must not be blank");
		}

		String userId = request.userId();
		String conversationId = request.conversationId();
		if (conversationId == null || conversationId.isBlank()) {
			conversationId = UUID.randomUUID().toString();
		}

		// Logical multi-tenant namespace: e.g. "cust-101:conv-1"
		String memoryConversationId = userId + ":" + conversationId;

		String reply = chatClient.prompt()
				.advisors(advisor -> advisor.advisors(memoryAdvisor).param(ChatMemory.CONVERSATION_ID, memoryConversationId))				
				.user(request.message())
				.call()
				.content();


		return new ChatResponse(conversationId, reply);
	}

	@ExceptionHandler(RuntimeException.class)
	public ProblemDetail handleChatProviderFailure() {
		return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
				"The chat service is temporarily unavailable.");
	}

	@ExceptionHandler(ResponseStatusException.class)
	public ProblemDetail handleRequestValidation(ResponseStatusException exception) {
		return ProblemDetail.forStatusAndDetail(exception.getStatusCode(), exception.getReason());
	}

	public record ChatRequest(String userId, String conversationId, String message) {
	}

	public record ChatResponse(String conversationId, String reply) {
	}
}
