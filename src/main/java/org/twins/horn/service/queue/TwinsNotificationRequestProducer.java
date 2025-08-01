package org.twins.horn.service.queue;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.twins.horn.notify.TwinsNotifyProto.InitializeNotificationRequest;

@Service
public class TwinsNotificationRequestProducer {
    private static final String QUEUE_NAME = "twins-initialize-notify";

    @Autowired
    private RabbitTemplate rabbitTemplate;

    /**
     * Producer for InitializeNotificationRequest for RabbitMQ queue "twins-initialize-notify"
     * from twins-notification.proto.
     */
    public void sendInitializeNotification(String clientId) {
        InitializeNotificationRequest request = InitializeNotificationRequest.newBuilder()
                .setClientId(clientId)
                .build();
        rabbitTemplate.convertAndSend(QUEUE_NAME, request.toByteArray());
    }
}
