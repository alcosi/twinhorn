package org.twins.horn;

import org.springframework.amqp.core.Queue;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.twins.horn.service.grpc.TwinfaceDataStreamingServer;
import org.twins.horn.service.grpc.TwinfaceGrpcNotifier;
import org.twins.horn.service.grpc.security.AuthInterceptor;
import org.twins.horn.service.queue.TwinsNotificationsConsumer;

import java.io.IOException;

/**
 * Entry point for the Horn micro-service.
 *
 * <p>Bootstraps the Spring context, configures infrastructure components and starts
 * the gRPC data-streaming layer:</p>
 *
 * <ul>
 *   <li>Runs {@link SpringApplication} with package scan rooted at {@code org.twins.horn}.</li>
 *   <li>Declares a durable RabbitMQ queue {@code twins-notify}; its message TTL is
 *       taken from the system property {@code twins-notify.ttl} (default&nbsp;60&nbsp;s).</li>
 *   <li>Instantiates and starts {@link TwinfaceDataStreamingServer} which forwards
 *       queue notifications to connected gRPC clients while securing calls via
 *       {@link AuthInterceptor}.</li>
 * </ul>
 *
 * The application terminates only when the gRPC server is shut down.
 */
@SpringBootApplication(scanBasePackages = "org.twins.horn")
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);

    }

    @Bean
    public Queue twinsNotificationQueue() {
        // durable queue for notifications to updater from front service with TTL
        String ttlProp = System.getProperty("twins-notify.ttl", "60000"); // default 60 seconds
        java.util.Map<String, Object> args = java.util.Map.of("x-message-ttl", Integer.parseInt(ttlProp));
        return new Queue("twins-notify", true, false, false, args);
    }

    @Bean
    public void TwinfaceDataStreamingServer(){
        TwinfaceGrpcNotifier twinfaceGrpcNotifier = new TwinfaceGrpcNotifier();
        final TwinfaceDataStreamingServer server = new TwinfaceDataStreamingServer(twinfaceGrpcNotifier, new TwinsNotificationsConsumer(twinfaceGrpcNotifier), new AuthInterceptor());
        try {
            server.start();
            server.server.awaitTermination();
        } catch (InterruptedException | IOException e) {//todo - handle properly
            throw new RuntimeException(e);
        }
    }
}
