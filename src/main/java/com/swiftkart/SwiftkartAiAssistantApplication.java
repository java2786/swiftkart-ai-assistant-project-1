package com.swiftkart;

import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.core.env.Environment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.swiftkart.agent.SwiftKartAgentOrchestrator;

@SpringBootApplication
public class SwiftkartAiAssistantApplication implements CommandLineRunner {

	private static final Logger logger = LoggerFactory.getLogger(SwiftkartAiAssistantApplication.class);

	@Autowired 
	Environment environment;
	public static void main(String[] args) {
		SpringApplication.run(SwiftkartAiAssistantApplication.class, args);
	}

	@Override
	public void run(String... args) throws Exception {


		String activeProfile = environment.getProperty("spring.profiles.active");
		logger.info("Active Profile: {}", activeProfile);
		logger.info("Demo Key: {}", environment.getProperty("demo.key"));

		sonarTest();
	}



	public static void sonarTest() {
        // 1. Critical Security Vulnerability: Hardcoded credentials (Rule: java:S1813 / java:S2068)
        String dbPassword = "SuperSecretPassword123!";
        String apiKey = "sk-proj-1234567890abcdef1234567890abcdef";

        // 2. Blocker Bug: Guaranteed NullPointerException / Null dereference (Rule: java:S2259)
        String value = null;
        if (value.length() > 0) {
            System.out.println(value);
        }

        // 3. Major Code Smell / Bug: Infinite recursion / dead loop (Rule: java:S2189)
        while (true) {
            int unusedCounter = 0;
            break;
        }

        // 4. Critical Security Flaw: Using Math.random for security/tokens instead of SecureRandom (Rule: java:S2245)
        double insecureToken = Math.random();
        System.out.println(dbPassword + apiKey + insecureToken);
    }
}


