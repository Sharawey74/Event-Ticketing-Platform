# Day 10 — Session Prompt
**Date:** Monday, April 13, 2026 | **Planned Hours:** 5 hrs

---

## YOUR FIRST MESSAGE TO COPILOT
> After pasting `intsructions.txt` content, send this as your next message:

```
We are on Day 10 — RabbitMQ Consumers + Notifications.
Feature: rabbitmq-notifications

Active fixes today:
- Fix 10.1 — IMPORTANT: Create DenyRefundNotificationAction so denied refunds notify users.
- Fix 10.2 — IMPORTANT: QR Code generation is ASYNC via ticket.generation.queue, not inside webhook handler.
- Cross-cutting: Fix CC-1 (Propagate X-Correlation-ID in message headers), Fix CC-2.

Pre-conditions confirmed:
- Day 9 complete: Stripe webhook functional and idempotent ✅
- Docker Desktop is RUNNING ✅

TDD MANDATORY — Integration Tests FIRST (Red phase):
Write NotificationListenerIntegrationTest using Testcontainers (rabbitmq:4-management) BEFORE implementing the listener.
  publishBookingConfirmedEvent_shouldGenerateQRCodeAsync_andSendEmail()
  publishRefundDeniedEvent_shouldSendEmail()
  publishTicketGenerationEvent_shouldSaveQRCodeToTicket()

Run ./mvnw test -Dtest=NotificationListenerIntegrationTest — ALL must FAIL before coding.

Non-negotiable rules:
- QR codes MUST be generated inside the queue consumer, never in the HTTP thread handling the request/webhook.
- X-Correlation-ID must be added to RabbitMQ message attributes.
- Use postgres:17, redis:7, rabbitmq:4-management Testconsumers.

Start with: Write NotificationListenerIntegrationTest class with test method signatures and Testcontainers setup for RabbitMQ.
```

---

## Context Briefing

**What we're building today:**
Day 10 wires up the RabbitMQ consumers — the message listeners that process booking confirmations, send email notifications, and generate QR codes. The infrastructure (queues, exchanges, bindings) was declared on Day 5. Today we implement the actual processing logic.

**Two critical fixes stand out today:**
1. **Fix 10.1** — The original plan has NO notification action for a denied refund. A user who requests a refund and gets denied receives zero feedback. This gap must be filled.
2. **Fix 10.2** — QR code generation is synchronous in the original plan. For a group booking of 50 tickets, that's 50 consecutive ZXing calls inside the webhook handler. This is unacceptable — it must be offloaded to `ticket.generation.queue`.

**Pre-conditions from Day 9:**
- Stripe checkout working ✅
- Booking transitions to CONFIRMED after payment ✅
- UNIQUE constraint on processed_stripe_events ✅
- Webhook idempotency tested ✅

---

## Active Plan Reference
- **Plan section:** Section 2 — Week 2, Day 10
- **RabbitMQ guide:** `Plans/Text/Phase1A_Sections 6,7,8,9_ImplementationGuides.txt`
- **Plan file to attach:** `Plans/Text/Phase1A_Section 2_ExecutionMap.txt`

---

## Fixes to Apply Today

| Fix ID | Severity | Action Required |
|--------|----------|-----------------|
| **Fix 10.1** | 🟡 IMPORTANT | Create `DenyRefundNotificationAction` — triggered by the `REFUND_REQUESTED → REFUND_DENIED` transition in the state machine. Without this, denied refund users receive NO feedback. |
| **Fix 10.2** | 🟡 IMPORTANT | QR code generation must be ASYNC — do NOT call ZXing inside the webhook handler. Instead, the `PAYMENT_SUCCESS` handler publishes a `TicketGenerationEvent` to `ticket.generation.queue`. The `QRCodeGenerationListener` processes it asynchronously. |

---

## Tasks (In Order)

### Morning (1 hr) — Research
- Read RabbitMQ AMQP concepts: topic vs direct exchanges, routing keys
- Read Dead Letter Queue (DLQ) pattern — understand what happens when a consumer throws repeatedly

### Afternoon (3.5 hrs) — Message Queues

#### BookingEventPublisher
```java
@Component
@RequiredArgsConstructor
public class BookingEventPublisher {
    private final RabbitTemplate rabbitTemplate;
    
    // Uses Jackson2JsonMessageConverter — configure in RabbitMQConfig
    
    public void publishBookingConfirmation(BookingConfirmedEvent event) {
        rabbitTemplate.convertAndSend("booking.exchange", "booking.confirmed", event);
    }
    
    public void publishEmailNotification(EmailNotificationEvent event) {
        rabbitTemplate.convertAndSend("notification.exchange", "email.send", event);
    }
    
    public void publishTicketGeneration(TicketGenerationEvent event) {  // Fix 10.2
        rabbitTemplate.convertAndSend("booking.exchange", "ticket.generate", event);
    }
}
```

Message POJOs (include all fields consumers will need):
- `BookingConfirmedEvent` — bookingId, userId, eventId, eventTitle, tickets, totalAmount
- `EmailNotificationEvent` — to, subject, body (HTML), templateType
- `TicketGenerationEvent` — ticketIds, bookingId, eventId (Fix 10.2 — async QR)
- `WaitlistAvailableEvent` — userId, eventId, tierId, availableCount

#### BookingNotificationListener (Fix 10.1 + Fix 10.2)
```java
@Component
@RequiredArgsConstructor
public class BookingNotificationListener {
    
    @RabbitListener(queues = "booking.confirmation.queue")
    public void handleBookingConfirmation(BookingConfirmedEvent event) {
        // 1. Publish ticket generation event (Fix 10.2 — async QR)
        publisher.publishTicketGeneration(new TicketGenerationEvent(event.getTicketIds(), ...));
        // 2. Send confirmation email via emailService
    }
    
    @RabbitListener(queues = "email.notification.queue")
    public void handleEmailNotification(EmailNotificationEvent event) {
        emailService.sendEmail(event.getTo(), event.getSubject(), event.getBody());
    }
    
    @RabbitListener(queues = "ticket.generation.queue")  // Fix 10.2
    public void handleTicketGeneration(TicketGenerationEvent event) {
        ticketService.generateQrCodes(event.getTicketIds());  // async — multiple ZXing calls here
    }
}
```

#### DenyRefundNotificationAction (Fix 10.1)
```java
@Component
@RequiredArgsConstructor
public class DenyRefundNotificationAction 
    implements Action<BookingState, BookingEvent> {
    
    private final BookingEventPublisher publisher;
    
    @Override
    public void execute(StateContext<BookingState, BookingEvent> context) {
        Long bookingId = (Long) context.getExtendedState().getVariables().get("bookingId");
        String reason = (String) context.getExtendedState().getVariables().get("denialReason");
        // Publish EMAIL notification: "Your refund request for booking #{bookingId} has been denied. Reason: {reason}"
        publisher.publishEmailNotification(new EmailNotificationEvent(...));
    }
}
```

Register in `BookingStateMachineConfig.java`: `REFUND_REQUESTED → REFUND_DENIED on DENY_REFUND (action: denyRefundNotificationAction)`

#### QR Code Generation
- Add ZXing: `com.google.zxing:core:3.5.1`, `com.google.zxing:javase:3.5.1`
- `QRCodeGeneratorUtil.generate(String content) → Base64 String`
- QR content: JSON `{ticketId, bookingId, eventId, seatNumber, userId}` signed with app secret
- Store in `tickets.qr_code` column

#### Email Service (Mailhog)
- Add Mailhog to docker-compose.yml (ports 1025 + 8025)
- Configure `JavaMailSender` bean
- `sendBookingConfirmation(BookingConfirmedEvent)` → HTML email with QR code as inline Base64 image

### Evening (1 hr) — Integration Test + Git
```java
@SpringBootTest + @Testcontainers (with RabbitMQ container)
// Publish BookingConfirmedEvent
// Awaitility.await().atMost(5, SECONDS).until(() -> qrCodeGenerated)
// Assert: email sent (mock EmailService), QR code stored in ticket
```
Git commit: `feat: implement rabbitmq message queues and email notifications`

---

## Expected Deliverable / Success Criteria

```
[ ] QR generation is ASYNC — in ticket.generation.queue listener, NOT in webhook handler (Fix 10.2)
[ ] DenyRefundNotificationAction created and wired to REFUND_DENIED transition (Fix 10.1)
[ ] All 3 RabbitMQ queues processing messages correctly
[ ] Mailhog receiving confirmation emails (check port 8025)
[ ] QR code Base64 stored in tickets table
[ ] NotificationListenerIntegrationTest passing
[ ] X-Correlation-ID propagated in all RabbitMQ message headers (Fix CC-1)
[ ] ./mvnw test — all tests green
```

---

## Skills to Attach This Session
- `Plans/skills/java-springboot.SKILL.md`

## ⚠️ Reminders
1. QR generation belongs in the `ticket.generation.queue` consumer, NOT in the Stripe webhook handler
2. `DenyRefundNotificationAction` must be wired in the SSM config — don't leave `DENY_REFUND` without an action
3. Use `X-Correlation-ID` in all RabbitMQ message headers — propagate from the original HTTP request via MDC
