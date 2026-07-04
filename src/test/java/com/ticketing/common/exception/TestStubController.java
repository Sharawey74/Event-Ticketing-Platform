package com.ticketing.common.exception;

import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/test")
class TestStubController {

    @PostMapping("/illegal-state")
    public void throwIllegalState() {
        throw new IllegalStateException("Checkout requires booking in RESERVED state, but was: CANCELLED");
    }

    @PostMapping("/optimistic-lock")
    public void throwOptimisticLockingFailure() {
        throw new ObjectOptimisticLockingFailureException("Booking", 42L);
    }
}
