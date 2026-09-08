package com.swiftkart.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.swiftkart.domain.Customer;

public interface CustomerRepository extends JpaRepository<Customer, String> {
}