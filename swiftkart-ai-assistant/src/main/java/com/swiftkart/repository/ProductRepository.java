package com.swiftkart.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.swiftkart.domain.Product;

public interface ProductRepository extends JpaRepository<Product, String> {
    List<Product> findByNameContainingIgnoreCase(String name);
}