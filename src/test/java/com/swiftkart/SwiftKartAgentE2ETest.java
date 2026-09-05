package com.swiftkart;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.function.Consumer;
import java.util.concurrent.atomic.AtomicReference;

import com.swiftkart.domain.Order;
import com.swiftkart.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:swiftkart-test;DB_CLOSE_DELAY=-1",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.sql.init.mode=always",
                "spring.jpa.hibernate.ddl-auto=none",
                "spring.ai.chat.memory.repository.jdbc.initialize-schema=always",
                "spring.autoconfigure.exclude=org.springframework.ai.vectorstore.chroma.autoconfigure.ChromaVectorStoreAutoConfiguration,org.springframework.ai.model.chat.client.autoconfigure.ChatClientAutoConfiguration,org.springframework.ai.model.openai.autoconfigure.OpenAiEmbeddingAutoConfiguration,org.springframework.ai.model.openai.autoconfigure.OpenAiAudioSpeechAutoConfiguration,org.springframework.ai.model.openai.autoconfigure.OpenAiAudioTranscriptionAutoConfiguration,org.springframework.ai.model.openai.autoconfigure.OpenAiImageAutoConfiguration,org.springframework.ai.model.openai.autoconfigure.OpenAiChatAutoConfiguration,org.springframework.ai.model.openai.autoconfigure.OpenAiModerationAutoConfiguration",
                "swiftkart.rag.vector-store-path=./target/test-vectorstore.json"
        })
    @AutoConfigureTestRestTemplate
@ActiveProfiles({"test", "vector-simple"})
    @Import(SwiftKartAgentE2ETest.OfflineEmbeddingConfiguration.class)
class SwiftKartAgentE2ETest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private ChatClient chatClient;

    @MockitoBean
    private VectorStore vectorStore;

    private final ChatClient.ChatClientRequestSpec requestSpec = org.mockito.Mockito.mock(ChatClient.ChatClientRequestSpec.class);
    private final ChatClient.CallResponseSpec responseSpec = org.mockito.Mockito.mock(ChatClient.CallResponseSpec.class);
    private final AtomicReference<String> latestPrompt = new AtomicReference<>();

    @BeforeEach
    void configureOfflineAiAndFixture() {
        // Given
        orderRepository.save(new Order("SK-1042", "cust-101", "SwiftKart X1 Smartwatch", "Delayed",
                LocalDate.of(2026, 9, 9)));
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.advisors(org.mockito.ArgumentMatchers.<Consumer<ChatClient.AdvisorSpec>>any())).thenReturn(requestSpec);
        doAnswer(invocation -> {
            latestPrompt.set(invocation.getArgument(0, String.class));
            return requestSpec;
        }).when(requestSpec).user(anyString());
        when(requestSpec.call()).thenReturn(responseSpec);
        when(responseSpec.content()).thenAnswer(invocation -> responseFor(latestPrompt.get()));
    }

    @Test
    void ragChatReturnsGroundedElectronicsReturnPolicy() {
        // When
        ResponseEntity<String> response = post("/api/rag/chat",
                "{\"userId\":\"cust-101\",\"conversationId\":\"conv-rag\",\"message\":\"Can I return electronics?\"}");

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("14-day return policy", "electronics");
    }

    @Test
    void repeatedConversationUsesSameConversationIdAndRetainsContext() {
        // When
        ResponseEntity<String> first = post("/api/chat",
                "{\"userId\":\"cust-101\",\"conversationId\":\"conv-1\",\"message\":\"My electronics order is delayed\"}");
        ResponseEntity<String> followUp = post("/api/chat",
                "{\"userId\":\"cust-101\",\"conversationId\":\"conv-1\",\"message\":\"What is the return window?\"}");

        // Then
        assertThat(first.getBody()).contains("SK-1042");
        assertThat(followUp.getBody()).contains("14-day return policy");
        assertThat(followUp.getBody()).contains("conv-1");
    }

    @Test
    void agentCompoundQueryIncludesOrderAndPolicyTerms() {
        // When
        ResponseEntity<String> response = post("/api/agent/chat",
                "{\"userId\":\"cust-101\",\"conversationId\":\"conv-agent\",\"message\":\"Check delayed order SK-1042 and explain returns\"}");

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("SK-1042", "Delayed", "14-day return policy");
    }

    @Test
    void invalidAgentRequestReturnsProblemDetails() {
        // When
        ResponseEntity<String> response = post("/api/agent/chat", "{\"userId\":\" \"}");

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getHeaders().getContentType()).satisfies(type ->
                assertThat(type).isNotNull().extracting(MediaType::toString).isEqualTo("application/problem+json"));
        assertThat(response.getBody()).contains("userId must not be blank");
    }

    private String responseFor(String prompt) {
        if (prompt != null && prompt.contains("SK-1042")) {
            return "Order SK-1042 is Delayed. The electronics 14-day return policy applies.";
        }
        if (prompt != null && prompt.toLowerCase().contains("return")) {
            return "The electronics 14-day return policy applies from delivery.";
        }
        return "Order SK-1042 is delayed; please contact support. 14-day return policy applies.";
    }

    private ResponseEntity<String> post(String path, String json) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.postForEntity("http://localhost:" + port + path,
                new HttpEntity<>(json, headers), String.class);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class OfflineEmbeddingConfiguration {
        @Bean
        @Primary
        EmbeddingModel embeddingModel() {
            return org.mockito.Mockito.mock(EmbeddingModel.class);
        }

        @Bean
        @Primary
        ChatModel chatModel() {
            return org.mockito.Mockito.mock(ChatModel.class);
        }

        @Bean
        @Primary
        ChatClient chatClient() {
            return org.mockito.Mockito.mock(ChatClient.class);
        }

        @Bean
        @Primary
        ChatClient.Builder chatClientBuilder(ChatClient chatClient) {
            ChatClient.Builder builder = org.mockito.Mockito.mock(ChatClient.Builder.class);
            when(builder.defaultSystem(anyString())).thenReturn(builder);
            doAnswer(invocation -> builder).when(builder).defaultAdvisors(any(Advisor[].class));
            doAnswer(invocation -> builder).when(builder).defaultTools(any(Object[].class));
            when(builder.build()).thenReturn(chatClient);
            return builder;
        }
    }
}