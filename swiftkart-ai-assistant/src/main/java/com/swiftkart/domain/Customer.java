package com.swiftkart.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "customers")
public class Customer {

    @Id
    private String id;
    private String name;
    private String city;
    private String email;

    protected Customer() {
    }

    public Customer(String id, String name, String city, String email) {
        this.id = id;
        this.name = name;
        this.city = city;
        this.email = email;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getCity() { return city; }
    public String getEmail() { return email; }
}