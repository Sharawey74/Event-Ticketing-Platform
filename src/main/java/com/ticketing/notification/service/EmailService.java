package com.ticketing.notification.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Email service stub for Phase 1A.
 * Logs the email send operation to console. In Phase 1B this will be
 * replaced with JavaMailSender + Mailhog/SendGrid integration.
 *
 * Non-negotiable rule: this service is the ONLY place email is sent.
 * Never call JavaMailSender directly from a listener or action.
 */
@Service
public class EmailService {

    private static final Logger logger = LoggerFactory.getLogger(EmailService.class);

    /**
     * Sends an email notification.
     *
     * @param to      recipient email address
     * @param subject email subject line
     * @param body    HTML email body
     */
    public void sendEmail(String to, String subject, String body) {
        logger.info("[EMAIL STUB] Sending email to={} subject='{}' bodyLength={}",
                to, subject, body != null ? body.length() : 0);
        // Phase 1B: inject JavaMailSender and send real email via Mailhog / SMTP
    }
}
