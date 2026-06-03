package com.ticketing.common.exception;

/**
 * Thrown when a request conflicts with the current state of a resource.
 * Maps to HTTP 409 Conflict in GlobalExceptionHandler.
 *
 * Examples:
 *  - Requesting a refund on a booking that is not CONFIRMED
 *  - Attempting to publish an event that is already published
 */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
