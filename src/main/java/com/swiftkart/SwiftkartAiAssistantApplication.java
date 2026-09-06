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
	}


}


