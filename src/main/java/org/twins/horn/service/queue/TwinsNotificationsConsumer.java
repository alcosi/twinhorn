package org.twins.horn.service.queue;

import com.google.protobuf.InvalidProtocolBufferException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.twins.horn.notify.TwinsNotifyProto.TwinsNotifyResponse;
import org.twins.horn.service.grpc.TwinfaceGrpcNotifier;
import org.twins.horn.util.InfrastructureErrorNotifier;
import org.twins.horn.util.RetryWithBackoffExecutor;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
public class TwinsNotificationsConsumer {
    private final TwinfaceGrpcNotifier grpcNotifier;
    private final CircuitBreaker circuitBreaker;

    @Value("${twins-notify.concurrency:3}")
    private int concurrency;

    // Counter for consecutive deserialization / payload failures
    private final AtomicInteger consecutivePayloadErrors = new AtomicInteger(0);

    @Value("${payload.error.threshold:5}")
    private int payloadErrorThreshold;

    public TwinsNotificationsConsumer(TwinfaceGrpcNotifier grpcNotifier,
                                      CircuitBreaker rabbitCircuitBreaker) {
        this.grpcNotifier = grpcNotifier;
        this.circuitBreaker = rabbitCircuitBreaker;
    }

    /**
     * Consumer service that listens to "twins-notify" RabbitMQ queue. The message payload is protobuf binary which contains
     * TwinsNotifyResponse from twins-notification.proto. For simplicity we parse it into the generated protobuf class. Pool size is
     * configurable through property: twins-notify.concurrency (default 3)
     */
    @RabbitListener(queues = "twins-notify", concurrency = "${twins-notify.concurrency:3}")
    public void handleNotification(byte[] message) {
        // Retry configuration – could be externalised to application.properties
        RetryWithBackoffExecutor retryExecutor = new RetryWithBackoffExecutor(
                5,                      // max attempts
                Duration.ofMillis(500), // initial delay
                Duration.ofSeconds(5),  // max delay
                2.0,                    // multiplier
                Duration.ofSeconds(20)  // max total wait
        );

        // 1) Deserialize payload.  On failure -> skip and count consecutive errors
        TwinsNotifyResponse response;
        try {
            response = TwinsNotifyResponse.parseFrom(message);
            // success – reset counter
            consecutivePayloadErrors.set(0);
        } catch (InvalidProtocolBufferException | RuntimeException serEx) {
            int failures = consecutivePayloadErrors.incrementAndGet();
            log.error("Failed to deserialize twins notification (consecutive {}): {}", failures, serEx.getMessage());
            if (failures > payloadErrorThreshold) {
                InfrastructureErrorNotifier.terminateStreamsDataLoss("Repeated payload deserialization errors (" + failures + ")");
            }
            return; // skip bad message
        }

        // 2) Normal processing with retry / transient handling
        try {
            retryExecutor.execute(() -> {
                        try {
                            circuitBreaker.executeRunnable(() -> {
                                try {
                                    log.debug("Processing TwinsNotifyResponse: {}", response);
                                    grpcNotifier.notifyClients(response);
                                } catch (Exception e) {
                                    throw new RuntimeException("Failed to handle twins notification", e);
                                }
                            });
                            return null; // Callable requires return value
                        } catch (CallNotPermittedException cbOpen) {
                            // Circuit breaker OPEN – treat as transient
                            throw new RuntimeException("RabbitMQ circuit breaker OPEN", cbOpen);
                        }
                    },
                    // onRetry
                    (attempt, ex) -> InfrastructureErrorNotifier.notifyTransientError(
                            "Retry " + attempt + " due to: " + ex.getMessage()),
                    // onExhausted
                    ex -> InfrastructureErrorNotifier.notifyUnavailable("RabbitMQ unavailable: " + ex.getMessage())
            );
        } catch (org.springframework.amqp.AmqpException permanent) {// Handle non-recoverable infrastructure errors
            log.error("Permanent infrastructure error detected – terminating streams", permanent);
            InfrastructureErrorNotifier.terminateStreams(permanent.getMessage());
        } catch (Exception ex) {
            if (ex instanceof NullPointerException || ex instanceof IllegalStateException) {
                // Internal logic error – terminate streams with INTERNAL status
                log.error("Internal logic error detected – terminating streams", ex);
                InfrastructureErrorNotifier.terminateStreams(ex.getMessage());
            } else {
                log.error("All retry attempts for twins notification failed", ex);
            }
        }
    }
}
