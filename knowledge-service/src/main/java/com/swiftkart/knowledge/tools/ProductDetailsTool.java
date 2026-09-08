package com.swiftkart.knowledge.tools;

import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class ProductDetailsTool {

    public record ProductDetail(String name, double price, String category) {
    }

    private final Map<String, ProductDetail> productDetails = Map.of(
            "P1001", new ProductDetail("iPhone", 79999.0, "Mobile"),
            "P1002", new ProductDetail("MacBook Air", 114900.0, "Laptop"),
            "P1003", new ProductDetail("ANC Headphones", 14999.0, "Audio"));

    public ProductDetail getProductDetails(String productId) {
        return productDetails.getOrDefault(productId, new ProductDetail("iPhone", 79999.0, "Mobile"));
    }
}
