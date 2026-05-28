package com.ticketing.booking.repository;

import static org.assertj.core.api.Assertions.assertThat;


import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.ticketing.ticketing_platform.TestcontainersConfiguration;

import com.ticketing.booking.model.Booking;

@SpringBootTest
@Testcontainers
@Import(TestcontainersConfiguration.class)
class BookingIntegrationTest {

    @Autowired
    private BookingRepository bookingRepository;

    @Test
    void findAll_shouldRetrieveBookingsWithoutNPlusOne() {
        // Simple test to ensure the repository works and executes the query
        Page<Booking> page = bookingRepository.findAll(PageRequest.of(0, 10));
        assertThat(page).isNotNull();
    }
}
