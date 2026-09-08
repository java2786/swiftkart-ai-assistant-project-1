package com.swiftkart.domain;

import java.math.BigDecimal;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "products")
public class Product {

    @Id
    private String id;
    private String name;
    private String category;
    private BigDecimal price;
    private Integer stock;

    protected Product() {
    }

    public Product(String id, String name, String category, BigDecimal price, Integer stock) {
        this.id = id;
        this.name = name;
        this.category = category;
        this.price = price;
        this.stock = stock;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getCategory() { return category; }
    public BigDecimal getPrice() { return price; }
    public Integer getStock() { return stock; }
}