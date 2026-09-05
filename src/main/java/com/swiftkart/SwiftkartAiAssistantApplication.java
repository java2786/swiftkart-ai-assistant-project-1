package com.swiftkart;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.core.env.Environment;

@SpringBootApplication
public class SwiftkartAiAssistantApplication implements CommandLineRunner {

	@Autowired 
	Environment environment;
	public static void main(String[] args) {
		SpringApplication.run(SwiftkartAiAssistantApplication.class, args);
	}

	@Override
	public void run(String... args) throws Exception {
		// String activeProfile = environment.getProperty("spring.profiles.active");
		// System.out.println("Active Profile: " + activeProfile);
		// System.out.println("Demo Key: " + environment.getProperty("demo.key"));
	}


}


