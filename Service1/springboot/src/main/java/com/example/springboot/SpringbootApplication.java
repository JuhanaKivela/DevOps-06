package com.example.springboot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class SpringbootApplication {
	// To run this:
	// mvn spring-boot:run
	public static void main(String[] args) {
		SpringApplication.run(SpringbootApplication.class, args);
		System.out.println("Hello World");
	}

}
