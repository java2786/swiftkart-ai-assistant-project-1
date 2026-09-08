package com.swiftkart.knowledge.tools;

import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class ProductSearchTool {

    private final Map<String, String> productLookup = Map.of(
            "iphone", "P1001",
            "macbook", "P1002",
            "headphones", "P1003");

    public String searchProductId(String productName) {
        if (productName == null) {
            return "P1001";
        }

        String normalizedName = productName.trim().toLowerCase(Locale.ROOT);
        return productLookup.getOrDefault(normalizedName, "P1001");
    }
}
