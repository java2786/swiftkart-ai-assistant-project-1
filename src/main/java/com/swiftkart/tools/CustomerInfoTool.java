package com.swiftkart.tools;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import com.swiftkart.domain.Customer;
import com.swiftkart.repository.CustomerRepository;

@Component
public class CustomerInfoTool {

    private static final Logger logger = LoggerFactory.getLogger(CustomerInfoTool.class);
    private final CustomerRepository customerRepository;

    public CustomerInfoTool(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    @Tool(description = "Lookup customer account details, profile city, and email by customer ID (e.g. cust-101)")
    public String getCustomerInfo(String customerId) {
        logger.info("Customer info tool invoked for customer ID: {}", customerId);
        if (customerId == null || customerId.isBlank()) {
            return "Please provide a customer ID to look up.";
        }

        return customerRepository.findById(customerId.trim())
                .map(this::formatCustomer)
                .orElse("Customer with ID " + customerId.trim() + " was not found.");
    }

    private String formatCustomer(Customer customer) {
        return "Customer " + customer.getId() + ": " + customer.getName()
                + ", city " + customer.getCity() + ", email " + customer.getEmail() + ".";
    }
}