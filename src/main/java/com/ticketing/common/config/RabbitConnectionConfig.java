package com.ticketing.common.config;

import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.util.StringUtils;

import lombok.extern.slf4j.Slf4j;

/**
 * Explicit RabbitMQ connection setup for production.
 *
 * <p>Why this exists: Spring Boot's auto-configured {@code CachingConnectionFactory}
 * (built from {@code RabbitProperties} via {@code spring.rabbitmq.uri}) was silently
 * falling back to the default {@code localhost:5672} in production, even though
 * {@code RABBITMQ_URL} was confirmed present and correctly formatted on Railway
 * (verified directly inside the running container with {@code printenv RABBITMQ_URL}).
 * Rather than continue guessing at Spring Boot's internal property-to-connection-details
 * resolution, this bean takes over that step explicitly: it reads the same
 * {@code spring.rabbitmq.uri} property, parses it directly with the RabbitMQ Java
 * client's own {@code ConnectionFactory.setUri(String)}, and refuses to start at all
 * if the value is missing or blank — instead of ever silently defaulting to localhost
 * again. Same fail-fast treatment already applied to {@code JWT_SECRET} (application
 * startup) and the CORS origin check in {@link WebConfig}.
 *
 * <p>Scoped to the {@code prod} profile only — local development keeps using the
 * auto-configured connection factory against the local Docker Compose RabbitMQ
 * instance on {@code localhost:5672}, which is correct there.
 */
@Slf4j
@Configuration
@Profile("prod")
public class RabbitConnectionConfig {

    private final String rabbitmqUri;

    public RabbitConnectionConfig(@Value("${spring.rabbitmq.uri:}") String rabbitmqUri) {
        this.rabbitmqUri = rabbitmqUri;
    }

    @Bean
    public ConnectionFactory connectionFactory() {
        if (!StringUtils.hasText(rabbitmqUri)) {
            throw new IllegalStateException(
                "RABBITMQ_URL is missing or blank in production — refusing to start with an "
                    + "unintended localhost:5672 fallback. Set RABBITMQ_URL to a valid amqp(s):// "
                    + "connection string (e.g. the CloudAMQP URL) in Railway's service variables.");
        }

        com.rabbitmq.client.ConnectionFactory rabbitClientFactory = new com.rabbitmq.client.ConnectionFactory();
        try {
            rabbitClientFactory.setUri(rabbitmqUri);
        } catch (Exception ex) {
            throw new IllegalStateException(
                "RABBITMQ_URL is set but is not a valid AMQP connection URI: " + ex.getMessage(), ex);
        }

        // Spring AMQP's CachingConnectionFactory has its own connection/topology recovery
        // logic. The raw RabbitMQ Java client enables automatic recovery by default, and
        // running both recovery mechanisms at once is a documented source of instability
        // (leaked channels/threads, deadlocks during topology recovery) — this is exactly
        // what the client's own "Automatic Recovery was Enabled" startup warning flags.
        // Spring Boot's own auto-configuration disables this for the same reason; do the
        // same here since this factory is built by hand.
        rabbitClientFactory.setAutomaticRecoveryEnabled(false);
        rabbitClientFactory.setTopologyRecoveryEnabled(false);

        log.info("RabbitMQ connection factory configured explicitly for host: {}", rabbitClientFactory.getHost());
        return new CachingConnectionFactory(rabbitClientFactory);
    }
}
