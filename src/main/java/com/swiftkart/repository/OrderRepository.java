package com.swiftkart.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.swiftkart.domain.Order;

public interface OrderRepository extends JpaRepository<Order, String> {
}