package com.swiftkart.domain;

import java.time.LocalDate;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "orders")
public class Order {

    @Id
    private String id;
    private String customerId;
    private String productName;
    private String status;
    private LocalDate expectedDeliveryDate;

    protected Order() {
    }

    public Order(String id, String customerId, String productName, String status, LocalDate expectedDeliveryDate) {
        this.id = id;
        this.customerId = customerId;
        this.productName = productName;
        this.status = status;
        this.expectedDeliveryDate = expectedDeliveryDate;
    }

    public String getId() { return id; }
    public String getCustomerId() { return customerId; }
    public String getProductName() { return productName; }
    public String getStatus() { return status; }
    public LocalDate getExpectedDeliveryDate() { return expectedDeliveryDate; }
}