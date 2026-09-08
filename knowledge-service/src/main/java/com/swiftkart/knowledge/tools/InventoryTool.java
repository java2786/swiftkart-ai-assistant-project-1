package com.swiftkart.knowledge.tools;

import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class InventoryTool {

    public record InventoryStatus(int availableQuantity, String status) {
    }

    private final Map<String, InventoryStatus> inventory = Map.of(
            "P1001", new InventoryStatus(12, "AVAILABLE"),
            "P1002", new InventoryStatus(5, "AVAILABLE"),
            "P1003", new InventoryStatus(0, "OUT_OF_STOCK"));

    public InventoryStatus getInventory(String productId) {
        return inventory.getOrDefault(productId, new InventoryStatus(0, "OUT_OF_STOCK"));
    }
}
