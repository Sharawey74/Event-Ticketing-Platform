package com.ticketing.event.service;

import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EventServiceTest {

    @Test
    @DisplayName("createEvent with valid data should return event")
    void createEvent_withValidData_shouldReturnEvent() {
        fail("Not implemented yet");
    }

    @Test
    @DisplayName("getEvent with missing id should throw not found")
    void getEvent_withNonExistentId_shouldThrowNotFoundException() {
        fail("Not implemented yet");
    }

    @Test
    @DisplayName("updateEvent as non organizer should throw forbidden")
    void updateEvent_asNonOrganizer_shouldThrowForbiddenException() {
        fail("Not implemented yet");
    }
}
