package com.swiftkart.service;

import org.springframework.stereotype.Service;

// @Service
public class BuggyService {

    // 1. Blocker Bug: Direct Null Pointer Dereference (java:S2259)
    public int getLength() {
        String str = null;
        return str.length(); 
    }

    // 2. Critical Vulnerability: Hardcoded Credential (java:S2068)
    public void connect() {
        String password = "admin_password_12345";
        System.out.println(password);
    }

    // 3. Blocker Bug: Infinite Loop without exit condition (java:S2189)
    public void infiniteLoop() {
        int i = 0;
        while (i >= 0) {
            i++;
        }
    }
}