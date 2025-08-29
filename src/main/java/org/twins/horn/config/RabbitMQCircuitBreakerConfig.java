package org.twins.horn.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.circuitbreaker.event.CircuitBreakerEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Spring configuration that defines a {@link CircuitBreaker} instance dedicated to
 * RabbitMQ operations.  The instance is configured programmatically so the service
 * works out-of-the-box without extra YAML / properties.
 *
 * <p>Parameters are aligned with the error-handling strategy discussed in the
 * design notes:</p>
 * <ul>
 *   <li>failureRateThreshold: 50 % within the sliding window</li>
 *   <li>slidingWindowSize:     last 50 calls (count-based)</li>
 *   <li>waitDurationInOpenState: 30 seconds before attempting HALF-OPEN</li>
 *   <li>permittedNumberOfCallsInHalfOpenState: 10 test calls</li>
 * </ul>
 *
 * <p>The bean is named <em>rabbitCircuitBreaker</em> so it can be injected via
 * constructor injection wherever RabbitMQ interactions occur.</p>
 */
@Slf4j
@Configuration
public class RabbitMQCircuitBreakerConfig {

    @Bean(name = "rabbitCircuitBreaker")
    public CircuitBreaker rabbitCircuitBreaker() {
        CircuitBreakerConfig cbConfig = CircuitBreakerConfig.custom()
                .failureRateThreshold(50f)
                .slidingWindowSize(50)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .permittedNumberOfCallsInHalfOpenState(10)
                .recordException(ex -> true) // treat every Exception as a failure
                .build();

        CircuitBreaker circuitBreaker = CircuitBreakerRegistry.of(cbConfig)
                .circuitBreaker("rabbit-cb");

        // Optional: log state transitions for observability
        circuitBreaker.getEventPublisher().onEvent(this::logEvent);

        return circuitBreaker;
    }

    private void logEvent(CircuitBreakerEvent event) {
        switch (event.getEventType()) {
            case STATE_TRANSITION ->
                    log.warn("RabbitMQ circuit breaker transitioned: {}", event);
            case ERROR ->
                    log.debug("RabbitMQ call failed: {}", event);
            default ->
                    log.trace("RabbitMQ CB event: {}", event);
        }
    }
}
