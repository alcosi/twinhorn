package org.twins.horn.exception;

/**
 * Represents a business-logic error raised while handling a RabbitMQ message
 * (e.g., invalid payload, missing data). Not retryable, caller should fail fast.
 */
public class TwinMQBusinessException extends RuntimeException {
    public TwinMQBusinessException(String message) {
        super(message);
    }
}
