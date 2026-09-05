package com.swiftkart.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.vectorstore.VectorStore;

import com.swiftkart.tools.DeliveryEstimateTool;
import com.swiftkart.tools.OrderStatusTool;
import com.swiftkart.tools.ProductInfoTool;
import com.swiftkart.tools.WebSearchTool;

class SwiftKartAgentOrchestratorTest {
    private final ChatClient.Builder builder = mock(ChatClient.Builder.class);
    private final ChatClient chatClient = mock(ChatClient.class);
    private final ChatMemory chatMemory = mock(ChatMemory.class);
    private final VectorStore vectorStore = mock(VectorStore.class);
    private final OrderStatusTool orderStatusTool = mock(OrderStatusTool.class);
    private final ProductInfoTool productInfoTool = mock(ProductInfoTool.class);
    private final DeliveryEstimateTool deliveryEstimateTool = mock(DeliveryEstimateTool.class);
    private final WebSearchTool webSearchTool = mock(WebSearchTool.class);

    @BeforeEach
    void configureBuilder() {
        when(builder.defaultSystem(any(String.class))).thenReturn(builder);
        doReturn(builder).when(builder).defaultAdvisors(any(Advisor[].class));
        doReturn(builder).when(builder).defaultTools(any(Object[].class));
        when(builder.build()).thenReturn(chatClient);
    }

    @Test
    void constructorAttachesMemoryAndQuestionAnswerAdvisorsAndTools() {
        // Given / When
        new SwiftKartAgentOrchestrator(builder, chatMemory, vectorStore, orderStatusTool,
                productInfoTool, deliveryEstimateTool, webSearchTool);

        // Then
        ArgumentCaptor<Advisor[]> advisorCaptor = ArgumentCaptor.forClass(Advisor[].class);
        verify(builder).defaultAdvisors(advisorCaptor.capture());
        assertThat(advisorCaptor.getValue()).hasSize(2);
        assertThat(advisorCaptor.getValue()).anyMatch(advisor -> advisor instanceof MessageChatMemoryAdvisor);
        assertThat(advisorCaptor.getValue()).anyMatch(advisor -> advisor.getClass().getSimpleName()
                .equals("QuestionAnswerAdvisor"));
        verify(builder).defaultTools(orderStatusTool, productInfoTool, deliveryEstimateTool, webSearchTool);
    }

    @Test
    void processBuildsMemoryScopedPromptAndReturnsReply() {
        // Given
        SwiftKartAgentOrchestrator orchestrator = new SwiftKartAgentOrchestrator(builder, chatMemory, vectorStore,
                orderStatusTool, productInfoTool, deliveryEstimateTool, webSearchTool);
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec responseSpec = mock(ChatClient.CallResponseSpec.class);
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.advisors(org.mockito.ArgumentMatchers.<Consumer<ChatClient.AdvisorSpec>>any())).thenReturn(requestSpec);
        when(requestSpec.user("Track my order")).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(responseSpec);
        when(responseSpec.content()).thenReturn("Your order is shipped.");

        // When
        String result = orchestrator.process("cust-101", "conv-1", "Track my order");

        // Then
        assertThat(result).isEqualTo("Your order is shipped.");
        verify(requestSpec).advisors(org.mockito.ArgumentMatchers.<Consumer<ChatClient.AdvisorSpec>>any());
        verify(requestSpec).user("Track my order");
    }

    @Test
    void blankUserIdIsRejectedAndBlankMessageGetsGuidance() {
        // Given
        SwiftKartAgentOrchestrator orchestrator = new SwiftKartAgentOrchestrator(builder, chatMemory, vectorStore,
                orderStatusTool, productInfoTool, deliveryEstimateTool, webSearchTool);

        // When / Then
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> orchestrator.process(" ", "conv-1", "Hello"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("userId must not be blank");
        assertThat(orchestrator.process("cust-101", "conv-1", " "))
                .isEqualTo("Please provide a message to process.");
    }
}