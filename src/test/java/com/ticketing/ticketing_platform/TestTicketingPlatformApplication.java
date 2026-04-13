package com.ticketing.ticketing_platform;

import org.springframework.boot.SpringApplication;

import com.ticketing.TicketingPlatformApplication;

public class TestTicketingPlatformApplication {

	public static void main(String[] args) {
		SpringApplication.from(TicketingPlatformApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
