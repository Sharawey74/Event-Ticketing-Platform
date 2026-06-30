package com.ticketing;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.retry.annotation.EnableRetry;

@SpringBootApplication
@EnableRetry
public class TicketingPlatformApplication {

	public static void main(String[] args) {
		SpringApplication.run(TicketingPlatformApplication.class, args);
	}

}
