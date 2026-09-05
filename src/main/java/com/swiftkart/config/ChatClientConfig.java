package com.swiftkart.config;

import com.swiftkart.tools.WebSearchTool;
import com.swiftkart.tools.CustomerInfoTool;
import com.swiftkart.tools.DeliveryEstimateTool;
import com.swiftkart.tools.OrderStatusTool;
import com.swiftkart.tools.ProductInfoTool;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
public class ChatClientConfig {

    @Bean
    @Profile("openai")
    public ChatClient openAiChatClient(OpenAiChatModel model, ChatMemory memory, WebSearchTool webSearchTool,
            OrderStatusTool orderStatusTool, ProductInfoTool productInfoTool, CustomerInfoTool customerInfoTool,
            DeliveryEstimateTool deliveryEstimateTool) {
        return createChatClient(model, memory, webSearchTool, orderStatusTool, productInfoTool, customerInfoTool,
                deliveryEstimateTool);
    }

    @Bean
    @Profile("ollama")
    public ChatClient ollamaChatClient(OllamaChatModel model, ChatMemory memory, WebSearchTool webSearchTool,
            OrderStatusTool orderStatusTool, ProductInfoTool productInfoTool, CustomerInfoTool customerInfoTool,
            DeliveryEstimateTool deliveryEstimateTool) {
        return createChatClient(model, memory, webSearchTool, orderStatusTool, productInfoTool, customerInfoTool,
                deliveryEstimateTool);
    }

    private ChatClient createChatClient(ChatModel model, ChatMemory memory, WebSearchTool webSearchTool,
            OrderStatusTool orderStatusTool, ProductInfoTool productInfoTool, CustomerInfoTool customerInfoTool,
            DeliveryEstimateTool deliveryEstimateTool) {
        return ChatClient.builder(model)
                // .defaultAdvisors(MessageChatMemoryAdvisor.builder(memory).build())
                .defaultTools(webSearchTool, orderStatusTool, productInfoTool, customerInfoTool, deliveryEstimateTool)
                .build();
    }
    
}