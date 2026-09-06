package com.ticketing.booking.service;

import java.util.Optional;

import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import com.ticketing.booking.model.Booking;
import com.ticketing.booking.repository.BookingRepository;
import com.ticketing.common.exception.ConflictException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Fix 26-idem — makes {@code POST /api/v1/bookings} idempotent on the {@code Idempotency-Key}
 * header: one key creates at most one booking, however many times the request arrives.
 *
 * <h2>Why this is a separate class</h2>
 *
 * The guard is a UNIQUE constraint (V14), so a duplicate surfaces as
 * {@link DataIntegrityViolationException}. That exception <b>cannot be handled inside</b>
 * {@code BookingService.reserveTickets} — by the time it is raised the transaction is marked
 * rollback-only, so a lookup for the winning row from in there would fail at commit. The catch has
 * to happen outside the transactional boundary, and Spring's {@code @Transactional} is proxy-based,
 * so a private helper or a self-call in the same class would not create that boundary. Hence a
 * distinct, deliberately non-transactional bean.
 *
 * <h2>Why not just check first</h2>
 *
 * A {@code existsByIdempotencyKey()} test before inserting is a check-then-act race: two concurrent
 * duplicates both see "not present" and both proceed. The database constraint is the only thing
 * that can arbitrate. The lookup below is a fast path for the common case (a retry seconds later),
 * not the guarantee — {@link #reserveTickets} is still correct with it removed.
 *
 * <p>This is the same shape as the Stripe webhook guard in {@code WebhookService} (Fix 9.2); the
 * difference is that a duplicate webhook is discarded, whereas a duplicate booking request must
 * return the original booking so the client can carry on to checkout.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BookingIdempotencyService {

    private final BookingService bookingService;
    private final BookingRepository bookingRepository;

    /**
     * Reserves seats, at most once per {@code idempotencyKey}.
     *
     * @param idempotencyKey the client's key; {@code null} or blank disables idempotency and
     *                       delegates straight through, which is what happens in local dev where
     *                       {@code app.rate-limit.enabled} is false and the header is optional
     * @return the newly created booking, or the one this key created earlier
     * @throws ConflictException if the key was used by a different user
     */
    public Booking reserveTickets(String idempotencyKey, Long userId, Long eventId, Long tierId, int quantity) {

        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return bookingService.reserveTickets(userId, eventId, tierId, quantity);
        }

        // Fast path: a retry arriving after the original committed. Costs one indexed read and
        // saves a pointless lock acquisition, inventory decrement and rollback.
        Optional<Booking> alreadyCreated = bookingRepository.findByIdempotencyKey(idempotencyKey);
        if (alreadyCreated.isPresent()) {
            Booking booking = requireOwnedBy(alreadyCreated.get(), userId);
            log.info("[{}] [booking] Idempotency-Key replay — returning existing booking {} instead of reserving again",
                    MDC.get("correlationId"), booking.getId());
            return booking;
        }

        try {
            return bookingService.reserveTickets(userId, eventId, tierId, quantity, idempotencyKey);

        } catch (DataIntegrityViolationException e) {
            // Two requests raced past the fast path. Postgres blocks the second INSERT on the
            // unique index until the first transaction resolves, so if we are here the winner has
            // committed and its row is visible to this fresh read.
            Booking winner = bookingRepository.findByIdempotencyKey(idempotencyKey)
                    // No row for this key means the violation came from some OTHER constraint —
                    // a real bug. Rethrow rather than reporting a phantom "duplicate".
                    .orElseThrow(() -> e);

            log.warn("[{}] [booking] Concurrent duplicate for Idempotency-Key — returning booking {} created by the winning request",
                    MDC.get("correlationId"), winner.getId());
            return requireOwnedBy(winner, userId);
        }
    }

    /**
     * A key is scoped to the user who first used it. Returning another user's booking would leak
     * their event, seat count and price, so a mismatch is a conflict rather than a replay.
     */
    private Booking requireOwnedBy(Booking booking, Long userId) {
        if (booking.getUser() == null || !booking.getUser().getId().equals(userId)) {
            log.warn("[{}] [booking] Idempotency-Key belongs to a different user — refusing replay of booking {}",
                    MDC.get("correlationId"), booking.getId());
            throw new ConflictException("This Idempotency-Key has already been used by a different request.");
        }
        return booking;
    }
}
