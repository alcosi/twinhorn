package org.twins.horn.exception;

/**
 * Indicates a temporary infrastructure-level failure when interacting with RabbitMQ
 * (e.g., connection reset, broker unavailable). Usually retryable.
 */
public class TwinMQTemporaryException extends RuntimeException {
    public TwinMQTemporaryException(String message, Throwable cause) {
        super(message, cause);
    }

    public TwinMQTemporaryException(String message) {
        super(message);
    }
}
