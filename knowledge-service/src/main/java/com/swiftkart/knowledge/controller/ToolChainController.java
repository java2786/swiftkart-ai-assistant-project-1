package com.swiftkart.knowledge.controller;

import com.swiftkart.knowledge.dto.ToolChainResponse;
import com.swiftkart.knowledge.service.ToolChainService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/knowledge/tools")
@Tag(name = "Tool Chaining", description = "Deterministic sequential tool chaining demonstration")
public class ToolChainController {

    private final ToolChainService toolChainService;

    public ToolChainController(ToolChainService toolChainService) {
        this.toolChainService = toolChainService;
    }

    @GetMapping("/chain")
    @Operation(summary = "Execute Tool Chain", description = "Runs ProductSearch -> ProductDetails -> Inventory sequential lookup")
    public ToolChainResponse chain(@RequestParam("product") String product) {
        return toolChainService.executeChain(product);
    }
}
