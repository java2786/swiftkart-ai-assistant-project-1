package com.swiftkart.tools;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import com.swiftkart.domain.Order;
import com.swiftkart.repository.OrderRepository;

@Component
public class OrderStatusTool {

    private static final Logger logger = LoggerFactory.getLogger(OrderStatusTool.class);
    private final OrderRepository orderRepository;

    public OrderStatusTool(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Tool(description = "Looks up the current lifecycle status (CREATED, SHIPPED, or OUT_FOR_DELIVERY) of a SwiftKart customer order by its order ID (e.g. SK-1001).")
    public String getOrderStatus(String orderId) {
        logger.info("Order status tool invoked for order ID: {}", orderId);
        if (orderId == null || orderId.isBlank()) {
            return "Please provide an order ID to look up.";
        }

        return orderRepository.findById(orderId.trim())
                .map(this::formatOrder)
                .orElse("Order with ID " + orderId.trim() + " was not found.");
    }

    private String formatOrder(Order order) {
        return "Order " + order.getId() + " for " + order.getProductName()
                + " is currently " + order.getStatus() + ".";
    }
}