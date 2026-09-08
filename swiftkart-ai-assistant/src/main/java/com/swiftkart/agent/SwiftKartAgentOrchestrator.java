package com.swiftkart.agent;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;

import com.swiftkart.tools.DeliveryEstimateTool;
import com.swiftkart.tools.OrderStatusTool;
import com.swiftkart.tools.ProductInfoTool;
import com.swiftkart.tools.WebSearchTool;

@Component
public class SwiftKartAgentOrchestrator {

    private static final Logger logger = LoggerFactory.getLogger(SwiftKartAgentOrchestrator.class);

    private static final String AGENT_SYSTEM_PROMPT = """
            You are the SwiftKart Autonomous Support Agent. You have access to business tools for live data and a knowledge base for policies.
            When handling user requests:
            1. Break down compound intents step-by-step.
            2. Use business tools to look up real-time orders, products, and deliveries first.
            3. Use document retrieval to check relevant return, shipping, or cancellation policies.
            4. Synthesize a unified, grounded, professional answer. Never guess IDs or policy rules.
            """;

    private final ChatClient chatClient;
    private final MessageChatMemoryAdvisor memoryAdvisor;

    public SwiftKartAgentOrchestrator(ChatClient.Builder chatClientBuilder, ChatMemory chatMemory, VectorStore vectorStore,
            OrderStatusTool orderStatusTool, ProductInfoTool productInfoTool,
            DeliveryEstimateTool deliveryEstimateTool, WebSearchTool webSearchTool) {

        this.memoryAdvisor = MessageChatMemoryAdvisor.builder(chatMemory).build();

        QuestionAnswerAdvisor questionAnswerAdvisor = QuestionAnswerAdvisor.builder(vectorStore)
                .searchRequest(SearchRequest.builder()
                        .topK(3)
                        .similarityThreshold(0.0)
                        .build())
                .promptTemplate(new PromptTemplate("""
                        You are the SwiftKart policy grounding assistant.
                        Use the retrieved policy documents to answer the user's question.
                        If the exact answer is not present in the retrieved documents, say that the specific policy is not available.

                        User question: {query}

                        Relevant policy documents:
                        {question_answer_context}
                        """))
                .build();

        this.chatClient = chatClientBuilder
                .defaultSystem(AGENT_SYSTEM_PROMPT)
                .defaultAdvisors(this.memoryAdvisor, questionAnswerAdvisor)
                .defaultTools(orderStatusTool, productInfoTool, deliveryEstimateTool, webSearchTool)
                .build();
    }

    public String process(String userId, String conversationId, String message) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId must not be blank");
        }
        if (message == null || message.isBlank()) {
            return "Please provide a message to process.";
        }

        String resolvedConversationId = (conversationId == null || conversationId.isBlank())
                ? UUID.randomUUID().toString()
                : conversationId;
        String memoryConversationId = userId + ":" + resolvedConversationId;

        logger.info("Agent orchestrator invoked for userId={}, conversationId={}, memoryConversationId={}",
                userId, resolvedConversationId, memoryConversationId);

        return chatClient.prompt()
                .advisors(advisor -> advisor.param(ChatMemory.CONVERSATION_ID, memoryConversationId))
                .user(message)
                .call()
                .content();
    }
}
