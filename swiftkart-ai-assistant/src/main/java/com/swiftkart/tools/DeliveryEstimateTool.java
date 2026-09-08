package com.swiftkart.tools;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import com.swiftkart.domain.Order;
import com.swiftkart.repository.OrderRepository;

@Component
public class DeliveryEstimateTool {

    private static final Logger logger = LoggerFactory.getLogger(DeliveryEstimateTool.class);
    private static final String DEFAULT_CARRIER = "SwiftExpress";
    private final OrderRepository orderRepository;

    public DeliveryEstimateTool(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Tool(description = "Calculates or retrieves the projected delivery estimate, shipping carrier, and expected delivery arrival date for a SwiftKart order using its order ID (e.g. SK-1001, SK-1042).")
    public String getDeliveryEstimate(String orderId) {
        logger.info("Delivery estimate tool invoked for order ID: {}", orderId);
        if (orderId == null || orderId.isBlank()) {
            return "Please provide an order ID to look up a delivery estimate.";
        }

        String normalizedOrderId = orderId.trim();
        return orderRepository.findById(normalizedOrderId)
                .map(this::formatDeliveryEstimate)
                .orElse("No delivery estimate found for order ID: " + normalizedOrderId);
    }

    private String formatDeliveryEstimate(Order order) {
        return "Delivery estimate for order " + order.getId()
                + ": carrier=" + DEFAULT_CARRIER
                + ", expectedDeliveryDate=" + order.getExpectedDeliveryDate()
                + ". Delivery status note: the projected arrival date is based on the current order record.";
    }
}