package com.swiftkart.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.swiftkart.domain.Order;
import com.swiftkart.repository.OrderRepository;

class DeliveryEstimateToolTest {
    private final OrderRepository repository = mock(OrderRepository.class);
    private final DeliveryEstimateTool tool = new DeliveryEstimateTool(repository);

    @Test
    void existingOrderReturnsDeliveryEstimate() {
        // Given
        Order order = new Order("SK-1042", "cust-101", "SwiftKart X1 Smartwatch", "Shipped",
                LocalDate.of(2026, 9, 9));
        when(repository.findById("SK-1042")).thenReturn(Optional.of(order));

        // When / Then
        assertThat(tool.getDeliveryEstimate("SK-1042"))
                .contains("SK-1042", "carrier=SwiftExpress", "expectedDeliveryDate=2026-09-09");
    }

    @Test
    void absentOrderReturnsGracefulMessage() {
        // Given
        when(repository.findById("SK-9999")).thenReturn(Optional.empty());

        // When / Then
        assertThat(tool.getDeliveryEstimate("SK-9999"))
                .isEqualTo("No delivery estimate found for order ID: SK-9999");
    }
}