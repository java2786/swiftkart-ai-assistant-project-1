package com.swiftkart.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.swiftkart.domain.Order;
import com.swiftkart.repository.OrderRepository;

class OrderStatusToolTest {
    private final OrderRepository repository = mock(OrderRepository.class);
    private final OrderStatusTool tool = new OrderStatusTool(repository);

    @Test
    void existingOrderReturnsFormattedStatus() {
        // Given
        Order order = new Order("SK-1042", "cust-101", "SwiftKart X1 Smartwatch", "Shipped",
                LocalDate.of(2026, 9, 9));
        when(repository.findById("SK-1042")).thenReturn(Optional.of(order));

        // When
        String result = tool.getOrderStatus(" SK-1042 ");

        // Then
        assertThat(result).isEqualTo("Order SK-1042 for SwiftKart X1 Smartwatch is currently Shipped.");
    }

    @Test
    void missingOrderReturnsGracefulMessage() {
        // Given
        when(repository.findById("SK-9999")).thenReturn(Optional.empty());

        // When / Then
        assertThat(tool.getOrderStatus("SK-9999")).isEqualTo("Order with ID SK-9999 was not found.");
    }
}