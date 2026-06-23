package com.ticketing.waitlist.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ticketing.inventory.service.InventoryService;
import com.ticketing.notification.event.EmailNotificationEvent;
import com.ticketing.notification.publisher.BookingEventPublisher;
import com.ticketing.waitlist.model.WaitlistEntry;
import com.ticketing.waitlist.repository.WaitlistRepository;

/**
 * TDD Red Phase — Day 11
 *
 * Tests for WaitlistService (pure unit tests using Mockito — zero Spring
 * context, zero DB).
 *
 * Fix 11.2 context: seats are released from inventory when a booking moves to
 * EXPIRED or
 * REFUND_APPROVED. ReleaseSeatsAction calls notifyWaitlist() so the next person
 * in line
 * gets an email notification.
 */
@ExtendWith(MockitoExtension.class)
class WaitlistServiceTest {

    @Mock
    private WaitlistRepository waitlistRepository;

    @Mock
    private InventoryService inventoryService;

    @Mock
    private BookingEventPublisher publisher;

    @InjectMocks
    private WaitlistService waitlistService;

    // ─────────────────────────────────────────────────────────────────────────
    // Test 1 — join waitlist when no seats available
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("joinWaitlist: inventory is 0 → saves WaitlistEntry and does NOT throw")
    void joinWaitlist_whenInventoryIsZero_shouldSaveWaitlistEntry() {
        // Arrange
        Long userId = 1L;
        Long tierId = 10L;
        when(inventoryService.getAvailableCount(tierId)).thenReturn(0);

        // Act
        waitlistService.joinWaitlist(userId, tierId);

        // Assert — WaitlistEntry was saved
        verify(waitlistRepository).save(any(WaitlistEntry.class));
    }

    @Test
    @DisplayName("joinWaitlist: inventory > 0 → throws BusinessRuleException (seats still available)")
    void joinWaitlist_whenInventoryIsAvailable_shouldThrowBusinessRuleException() {
        // Arrange
        Long userId = 1L;
        Long tierId = 10L;
        when(inventoryService.getAvailableCount(tierId)).thenReturn(5);

        // Act + Assert — should not be able to join waitlist when seats exist
        assertThatThrownBy(() -> waitlistService.joinWaitlist(userId, tierId))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Seats still available");

        verify(waitlistRepository, never()).save(any());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Test 2 — notify waitlist when a seat is released
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("notifyWaitlist: seat released → publishes email notification to top of queue")
    void notifyWaitlist_whenSeatIsReleased_shouldPublishNotificationEvent() {
        // Arrange
        Long tierId = 10L;
        int releasedSeats = 1;

        WaitlistEntry entry = WaitlistEntry.builder()
                .userId(42L)
                .tierId(tierId)
                .userEmail("waiting-user@example.com")
                .build();

        when(waitlistRepository.findTopByTierIdOrderByCreatedAtAsc(tierId, releasedSeats))
                .thenReturn(List.of(entry));

        // Act
        waitlistService.notifyWaitlist(tierId, releasedSeats);

        // Assert — email notification published for the waiting user
        verify(publisher).publishEmailNotification(any(EmailNotificationEvent.class));
    }

    @Test
    @DisplayName("notifyWaitlist: no waitlist entries → publishes nothing")
    void notifyWaitlist_whenWaitlistIsEmpty_shouldPublishNothing() {
        // Arrange
        Long tierId = 10L;
        when(waitlistRepository.findTopByTierIdOrderByCreatedAtAsc(tierId, 1))
                .thenReturn(List.of());

        // Act
        waitlistService.notifyWaitlist(tierId, 1);

        // Assert — no email published
        verify(publisher, never()).publishEmailNotification(any());
    }
}
