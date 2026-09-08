package com.swiftkart.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.swiftkart.agent.SwiftKartAgentOrchestrator;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@Disabled ("Temporarily disabled")
@WebMvcTest(AgentController.class)
class AgentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SwiftKartAgentOrchestrator orchestrator;

    @Test
    void compoundPromptReturnsSynthesizedReply() throws Exception {
        // Given
        when(orchestrator.process(eq("cust-101"), eq("conv-1"), eq("Check my order and return policy")))
                .thenReturn("Order is shipped; electronics are returnable within 14 days.");

        // When / Then
        mockMvc.perform(post("/api/agent/chat")
                        .contentType("application/json")
                        .content("{\"userId\":\"cust-101\",\"conversationId\":\"conv-1\",\"message\":\"Check my order and return policy\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conversationId").value("conv-1"))
                .andExpect(jsonPath("$.reply").value("Order is shipped; electronics are returnable within 14 days."));
    }

    @Test
    void blankUserIdReturnsBadRequest() throws Exception {
        // Given / When / Then
        mockMvc.perform(post("/api/agent/chat")
                        .contentType("application/json")
                        .content("{\"userId\":\"\",\"message\":\"Hello\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void nullRequestBodyReturnsBadRequest() throws Exception {
        // Given / When / Then
        mockMvc.perform(post("/api/agent/chat")
                        .contentType("application/json"))
                .andExpect(status().isBadRequest());
    }
}