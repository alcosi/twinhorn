package org.twins.horn.service.grpc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.twins.horn.notify.TwinsNotifyProto.TwinsNotifyResponse;
import org.twins.horn.subscribe.TwinfaceSubscribeProto;
import org.twins.horn.subscribe.TwinfaceSubscribeProto.TwinfaceSubscribeUpdate;

import java.time.Instant;
import java.util.Collections;
import java.util.UUID;

/**
 * Service responsible for notifying gRPC clients about updates
 * received from RabbitMQ notifications.
 */
@Service
public class TwinfaceGrpcNotifier {
    private static final Logger logger = LoggerFactory.getLogger(TwinfaceGrpcNotifier.class);

    /**
     * Processes a TwinsNotifyResponse from RabbitMQ and notifies
     * all relevant gRPC clients about the update.
     * 
     * @param response The notification response from RabbitMQ
     */
    public void notifyClients(TwinsNotifyResponse response) {
        if (response == null || response.getTwinId().isEmpty()) {
            logger.warn("Received empty or invalid notification");
            return;
        }

        String twinId = response.getTwinId();
        logger.debug("Processing notification for twin ID: {} to clients: {}", 
                twinId, response.getClientIdsList());

        // Create the update message
        TwinfaceSubscribeUpdate update = TwinfaceSubscribeUpdate.newBuilder()
                .setUpdateId(response.getUpdateId())
                .setEventType(TwinfaceSubscribeProto.TwinEventType.TWIN_UPDATE)
                .setTimestamp(response.getTimestamp())
                .addAllUpdatedTwinIds(Collections.singletonList(twinId))
                .setStatus(convertResponseStatus(response.getStatus()))
                .build();

        // For each client in the notification
        for (String clientId : response.getClientIdsList()) {
            try {
                // Send update to the client's stream
                ConnectionRegistry.broadcast(clientId, update);
                logger.debug("Sent update for twin {} to client {}", twinId, clientId);
            } catch (Exception e) {
                logger.error("Failed to notify client {} about twin {}: {}", 
                        clientId, twinId, e.getMessage());
            }
        }
    }

    /**
     * Converts notification response status to gRPC update status
     */
    private TwinfaceSubscribeProto.UpdateStatus convertResponseStatus(
            org.twins.horn.notify.TwinsNotifyProto.ResponseStatus status) {
        switch (status) {
            case SUCCESS:
                return TwinfaceSubscribeProto.UpdateStatus.SUCCESS;
            case ERROR:
                return TwinfaceSubscribeProto.UpdateStatus.ERROR;
            case PARTIAL:
                return TwinfaceSubscribeProto.UpdateStatus.PARTIAL;
            default:
                return TwinfaceSubscribeProto.UpdateStatus.ERROR;
        }
    }
}
