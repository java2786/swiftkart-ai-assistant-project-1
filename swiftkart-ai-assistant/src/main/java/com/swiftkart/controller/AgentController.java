package com.swiftkart.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.server.ResponseStatusException;

import com.swiftkart.agent.SwiftKartAgentOrchestrator;

@RestController
@RequestMapping("/api/agent")
public class AgentController {

    private final SwiftKartAgentOrchestrator orchestrator;

    public AgentController(SwiftKartAgentOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @PostMapping("/chat")
    public AgentResponse chat(@RequestBody AgentRequest request) {
        if (request == null || request.userId() == null || request.userId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "userId must not be blank");
        }

        String conversationId = request.conversationId();
        if (conversationId == null || conversationId.isBlank()) {
            conversationId = UUID.randomUUID().toString();
        }

        String reply = orchestrator.process(request.userId(), conversationId, request.message());
        return new AgentResponse(conversationId, reply);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ProblemDetail handleRequestValidation(ResponseStatusException exception) {
        return ProblemDetail.forStatusAndDetail(exception.getStatusCode(), exception.getReason());
    }

    public record AgentRequest(String userId, String conversationId, String message) {
    }

    public record AgentResponse(String conversationId, String reply) {
    }
}
