package com.swiftkart.controller;

import static org.mockito.Mockito.when;

import java.util.function.Consumer;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.beans.factory.annotation.Autowired;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Disabled ("Temporarily disabled")
@WebMvcTest(ChatController.class)
class ChatControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ChatClient chatClient;

    @MockitoBean
    private ChatMemory chatMemory;

    @Test
    void validRequestPreservesConversationIdAndReturnsReply() throws Exception {
        // Given
        ChatClient.ChatClientRequestSpec requestSpec = org.mockito.Mockito.mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec responseSpec = org.mockito.Mockito.mock(ChatClient.CallResponseSpec.class);
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.advisors(org.mockito.ArgumentMatchers.<Consumer<ChatClient.AdvisorSpec>>any())).thenReturn(requestSpec);
        when(requestSpec.user("Where is my order?")).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(responseSpec);
        when(responseSpec.content()).thenReturn("Your order is on the way.");

        // When / Then
        mockMvc.perform(post("/api/chat")
                        .contentType("application/json")
                        .content("{\"userId\":\"cust-101\",\"conversationId\":\"conv-1\",\"message\":\"Where is my order?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conversationId").value("conv-1"))
                .andExpect(jsonPath("$.reply").value("Your order is on the way."));
    }

    @Test
    void missingConversationIdGeneratesUuid() throws Exception {
        // Given
        ChatClient.ChatClientRequestSpec requestSpec = org.mockito.Mockito.mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec responseSpec = org.mockito.Mockito.mock(ChatClient.CallResponseSpec.class);
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.advisors(org.mockito.ArgumentMatchers.<Consumer<ChatClient.AdvisorSpec>>any())).thenReturn(requestSpec);
        when(requestSpec.user("Hello")).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(responseSpec);
        when(responseSpec.content()).thenReturn("Hello!");

        // When / Then
        mockMvc.perform(post("/api/chat")
                        .contentType("application/json")
                        .content("{\"userId\":\"cust-101\",\"message\":\"Hello\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conversationId").isString())
                .andExpect(jsonPath("$.reply").value("Hello!"));
    }

    @Test
    void blankUserIdReturnsBadRequest() throws Exception {
        // Given / When / Then
        mockMvc.perform(post("/api/chat")
                        .contentType("application/json")
                        .content("{\"userId\":\" \u0020\",\"message\":\"Hello\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void downstreamFailureReturnsInternalServerErrorWithoutStackTrace() throws Exception {
        // Given
        ChatClient.ChatClientRequestSpec requestSpec = org.mockito.Mockito.mock(ChatClient.ChatClientRequestSpec.class);
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.advisors(org.mockito.ArgumentMatchers.<Consumer<ChatClient.AdvisorSpec>>any())).thenReturn(requestSpec);
        when(requestSpec.user("Hello")).thenReturn(requestSpec);
        when(requestSpec.call()).thenThrow(new RuntimeException("provider failed"));

        // When / Then
        mockMvc.perform(post("/api/chat")
                        .contentType("application/json")
                        .content("{\"userId\":\"cust-101\",\"message\":\"Hello\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.trace").doesNotExist());
    }
}