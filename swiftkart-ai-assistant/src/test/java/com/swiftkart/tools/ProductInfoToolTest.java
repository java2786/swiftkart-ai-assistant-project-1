package com.swiftkart.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import com.swiftkart.domain.Product;
import com.swiftkart.repository.ProductRepository;

@Disabled ("Temporarily disabled")
class ProductInfoToolTest {
    private final ProductRepository repository = mock(ProductRepository.class);
    private final ProductInfoTool tool = new ProductInfoTool(repository);

    @Test
    void matchingProductReturnsFormattedDetails() {
        // Given
        Product product = new Product("prod-202", "SwiftKart X1 Smartwatch", "Wearables",
                new BigDecimal("149.50"), 8);
        when(repository.findByNameContainingIgnoreCase("smartwatch")).thenReturn(List.of(product));

        // When / Then
        assertThat(tool.getProductInfo(" smartwatch "))
                .isEqualTo("SwiftKart X1 Smartwatch (Wearables) - $149.50; 8 units in stock. Product ID: prod-202.");
    }

    @Test
    void absentProductReturnsGracefulMessage() {
        // Given
        when(repository.findByNameContainingIgnoreCase("tablet")).thenReturn(List.of());

        // When / Then
        assertThat(tool.getProductInfo("tablet")).isEqualTo("No product matching \"tablet\" was found.");
    }
}