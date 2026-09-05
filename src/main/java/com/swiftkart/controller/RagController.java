package com.swiftkart.controller;

import com.swiftkart.rag.InMemoryRagStore;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.UUID;

/**
 * RAG (Retrieval-Augmented Generation) Chat Controller.
 * 
 * This controller provides an endpoint for chat requests that are grounded
 * in company policy documents via the InMemoryRagStore.
 * 
 * The RAG system automatically retrieves relevant policy chunks based on
 * semantic similarity and augments the user message with retrieved context.
 * Responses are grounded in actual policies and prevent hallucinations.
 */
@RestController
@RequestMapping("/api/rag/chat")
public class RagController {

    private final ChatClient chatClient;
    private final MessageChatMemoryAdvisor memoryAdvisor;
    private final InMemoryRagStore ragStore;

    public RagController(ChatClient chatClient, ChatMemory chatMemory, InMemoryRagStore ragStore) {
        this.chatClient = chatClient;
        this.memoryAdvisor = MessageChatMemoryAdvisor.builder(chatMemory).build();
        this.ragStore = ragStore;
    }

    /**
     * Chat endpoint with RAG support.
     * 
     * Combines:
     * 1. Conversational Memory: Previous messages in the conversation
     * 2. RAG Retrieval: Relevant policy document chunks from in-memory store
     * 3. LLM Generation: Response grounded in both memory and policies
     * 
     * Process:
     * - Search RAG store for relevant policy chunks
     * - Augment user message with retrieved context
     * - Send augmented message to LLM along with conversation history
     * 
     * @param request Chat request with userId, conversationId, and message
     * @return Chat response with conversationId and reply
     */
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

        // Retrieve relevant policy chunks using RAG
        List<String> relevantChunks = ragStore.search(request.message(), 3);
        
        // Augment the user message with retrieved context
        String augmentedMessage = buildAugmentedPrompt(request.message(), relevantChunks);

        // Send augmented message to LLM with memory advisor
        String reply = chatClient.prompt()
                .advisors(advisor -> advisor
                        .advisors(memoryAdvisor)
                        .param(ChatMemory.CONVERSATION_ID, memoryConversationId)
                )
                .user(augmentedMessage)
                .call()
                .content();

        return new ChatResponse(conversationId, reply);
    }

    /**
     * Build augmented prompt with retrieved policy context.
     * 
     * Constructs a prompt that includes:
     * 1. System instruction about grounding in company policies
     * 2. Retrieved relevant policy chunks as context
     * 3. The original user message
     * 
     * @param userMessage The original user question
     * @param relevantChunks Policy chunks retrieved from the RAG store
     * @return Augmented prompt string
     */
    private String buildAugmentedPrompt(String userMessage, List<String> relevantChunks) {
        StringBuilder prompt = new StringBuilder();
        
        prompt.append("You are a helpful SwiftKart customer support chatbot. ");
        prompt.append("Answer the following question based on SwiftKart's policies and procedures. ");
        prompt.append("If the information is not available in the provided context, politely indicate that you don't have specific information about that topic.\n\n");
        
        if (!relevantChunks.isEmpty()) {
            prompt.append("RELEVANT COMPANY POLICIES:\n");
            prompt.append("========================================\n");
            for (int i = 0; i < relevantChunks.size(); i++) {
                prompt.append("Policy Context ").append(i + 1).append(":\n");
                prompt.append(relevantChunks.get(i)).append("\n\n");
            }
            prompt.append("========================================\n\n");
        } else {
            prompt.append("(No specific policy information found for this query)\n\n");
        }
        
        prompt.append("CUSTOMER QUESTION:\n");
        prompt.append(userMessage);
        
        return prompt.toString();
    }

    /**
     * Request DTO for RAG chat endpoint.
     * 
     * @param userId Unique identifier for the customer
     * @param conversationId Optional conversation identifier; generated if not provided
     * @param message User's question or statement
     */
    public record ChatRequest(String userId, String conversationId, String message) {
    }

    /**
     * Response DTO for RAG chat endpoint.
     * 
     * @param conversationId Conversation identifier for tracking
     * @param reply LLM's response grounded in policies and conversation history
     */
    public record ChatResponse(String conversationId, String reply) {
    }
}

