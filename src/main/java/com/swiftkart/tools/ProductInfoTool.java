package com.swiftkart.tools;

import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import com.swiftkart.domain.Product;
import com.swiftkart.repository.ProductRepository;

@Component
public class ProductInfoTool {

    private static final Logger logger = LoggerFactory.getLogger(ProductInfoTool.class);
    private final ProductRepository productRepository;

    public ProductInfoTool(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Tool(description = "Lookup product specifications, price, and inventory stock availability by product name or keyword")
    public String getProductInfo(String productName) {
        logger.info("Product info tool invoked for product keyword: {}", productName);
        if (productName == null || productName.isBlank()) {
            return "Please provide a product name or keyword to look up.";
        }

        List<Product> products = productRepository.findByNameContainingIgnoreCase(productName.trim());
        if (products.isEmpty()) {
            return "No product matching \"" + productName.trim() + "\" was found.";
        }
        return products.stream().map(this::formatProduct).collect(Collectors.joining("\n"));
    }

    private String formatProduct(Product product) {
        String availability = product.getStock() > 0
                ? product.getStock() + " units in stock"
                : "currently out of stock";
        return product.getName() + " (" + product.getCategory() + ") - $"
                + product.getPrice() + "; " + availability + ". Product ID: " + product.getId() + ".";
    }
}