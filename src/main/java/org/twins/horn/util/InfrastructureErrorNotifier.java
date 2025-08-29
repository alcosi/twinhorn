package org.twins.horn.util;

import lombok.extern.slf4j.Slf4j;
import org.twins.horn.service.grpc.ConnectionRegistry;
import org.twins.horn.subscribe.TwinfaceSubscribeProto;
import org.twins.horn.subscribe.TwinfaceSubscribeProto.TwinfaceSubscribeUpdate;

import java.time.Instant;
import java.util.UUID;

/**
 * Emits heartbeat updates to all connected gRPC clients to indicate service-side
 * infrastructure health while long-running retry loops are in progress.
 *
 * <ul>
 *   <li>{@link #notifyTransientError(String)} — sent periodically while the
 *       service is retrying an operation after a temporary infrastructure
 *       glitch (RabbitMQ offline, network hiccup, DB timeout, etc.).</li>
 *   <li>{@link #notifyUnavailable(String)} — emitted once the retry logic gives
 *       up. Clients should treat the stream as degraded and may reconnect
 *       later.</li>
 * </ul>
 */
@Slf4j
public class InfrastructureErrorNotifier {
    private static final io.grpc.Status PERMANENT_STATUS = io.grpc.Status.INTERNAL;

    private InfrastructureErrorNotifier() {
    }

    public static void notifyTransientError(String message) {
        TwinfaceSubscribeUpdate update = buildUpdate(TwinfaceSubscribeProto.UpdateStatus.TRANSIENT_ERROR, message);
        ConnectionRegistry.broadcastAll(update);
        log.debug("Broadcasted TRANSIENT_ERROR heartbeat – {}", message);
    }

    public static void notifyUnavailable(String message) {
        TwinfaceSubscribeUpdate update = buildUpdate(TwinfaceSubscribeProto.UpdateStatus.UNAVAILABLE, message);
        ConnectionRegistry.broadcastAll(update);
        log.warn("Broadcasted UNAVAILABLE heartbeat – {}", message);
    }

    /**
     * Irrecoverable infrastructure failure – close every stream with INTERNAL status.
     */
    public static void terminateStreams(String message) {
        io.grpc.StatusRuntimeException error = io.grpc.Status.INTERNAL.withDescription(message).asRuntimeException();
        ConnectionRegistry.failAll(error);
        log.error("Closed all streams due to permanent error: {}", message);
    }

    /**
     * Close all streams with DATA_LOSS status after unrecoverable payload errors.
     */
    public static void terminateStreamsDataLoss(String message) {
        io.grpc.StatusRuntimeException error = io.grpc.Status.DATA_LOSS.withDescription(message).asRuntimeException();
        ConnectionRegistry.failAll(error);
        log.error("Closed all streams due to DATA_LOSS: {}", message);
    }

    private static TwinfaceSubscribeUpdate buildUpdate(TwinfaceSubscribeProto.UpdateStatus status, String message) {
        return TwinfaceSubscribeUpdate.newBuilder()
                .setUpdateId(UUID.randomUUID().toString())
                .setTimestamp(Instant.now().toString())
                .setStatus(status)
                .setEventType(TwinfaceSubscribeProto.TwinEventType.TWIN_UPDATE) // generic event type
                .setErrorMessage(message == null ? "" : message)
                .build();
    }
}
