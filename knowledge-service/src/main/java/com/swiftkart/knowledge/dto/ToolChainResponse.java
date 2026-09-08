package com.swiftkart.knowledge.dto;

import com.swiftkart.knowledge.tools.InventoryTool;
import com.swiftkart.knowledge.tools.ProductDetailsTool;
import java.util.List;

public record ToolChainResponse(
        String product,
        List<String> steps,
        String productId,
        ProductDetailsTool.ProductDetail details,
        InventoryTool.InventoryStatus inventory) {
}
