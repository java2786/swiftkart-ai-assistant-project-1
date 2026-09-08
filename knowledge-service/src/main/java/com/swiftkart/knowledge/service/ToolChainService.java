package com.swiftkart.knowledge.service;

import com.swiftkart.knowledge.dto.ToolChainResponse;
import com.swiftkart.knowledge.tools.InventoryTool;
import com.swiftkart.knowledge.tools.ProductDetailsTool;
import com.swiftkart.knowledge.tools.ProductSearchTool;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ToolChainService {

    private final ProductSearchTool productSearchTool;
    private final ProductDetailsTool productDetailsTool;
    private final InventoryTool inventoryTool;

    public ToolChainService(ProductSearchTool productSearchTool,
                            ProductDetailsTool productDetailsTool,
                            InventoryTool inventoryTool) {
        this.productSearchTool = productSearchTool;
        this.productDetailsTool = productDetailsTool;
        this.inventoryTool = inventoryTool;
    }

    public ToolChainResponse executeChain(String productName) {
        String productId = productSearchTool.searchProductId(productName);
        ProductDetailsTool.ProductDetail details = productDetailsTool.getProductDetails(productId);
        InventoryTool.InventoryStatus inventory = inventoryTool.getInventory(productId);

        List<String> steps = List.of(
                "ProductSearchTool.searchProductId(" + productName + ")",
                "ProductDetailsTool.getProductDetails(" + productId + ")",
                "InventoryTool.getInventory(" + productId + ")");

        return new ToolChainResponse(
                details.name(),
                steps,
                productId,
                details,
                inventory);
    }
}
