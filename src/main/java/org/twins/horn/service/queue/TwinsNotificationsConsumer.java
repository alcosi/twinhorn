package org.twins.horn.service.queue;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.twins.horn.notify.TwinsNotifyProto.TwinsNotifyResponse;
import org.twins.horn.service.grpc.TwinfaceGrpcNotifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class TwinsNotificationsConsumer {
    private static final Logger logger = LoggerFactory.getLogger(TwinsNotificationsConsumer.class);

    private final TwinfaceGrpcNotifier grpcNotifier;

    @Value("${twins-notify.concurrency:3}")
    private int concurrency;

    public TwinsNotificationsConsumer(TwinfaceGrpcNotifier grpcNotifier) {
        this.grpcNotifier = grpcNotifier;
    }

    /**
     * Consumer service that listens to "twins-notify" RabbitMQ queue. The message payload is protobuf binary which contains
     * TwinsNotifyResponse from twins-notification.proto. For simplicity we parse it into the generated protobuf class. Pool size is
     * configurable through property: twins-notify.concurrency (default 3)
     */
    @RabbitListener(queues = "twins-notify", concurrency = "${twins-notify.concurrency:3}")
    public void handleNotification(byte[] message) {
        try {
            TwinsNotifyResponse response = TwinsNotifyResponse.parseFrom(message);
            logger.info("Received TwinsNotifyResponse: {}", response);

            // Forward to gRPC service for distribution to clients
            grpcNotifier.notifyClients(response);
        } catch (Exception e) {
            logger.error("Failed to parse TwinsNotifyResponse from message", e);
        }
    }
}
