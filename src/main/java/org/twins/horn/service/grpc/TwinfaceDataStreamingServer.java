package org.twins.horn.service.grpc;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.twins.horn.service.auth.dto.TokenIntrospectRsDTOv1;
import org.twins.horn.service.grpc.security.AuthInterceptor;
import org.twins.horn.service.queue.TwinsNotificationsConsumer;
import org.twins.horn.subscribe.TwinfaceSubscribeProto;
import org.twins.horn.subscribe.TwinfaceSubscribeProto.TwinfaceSubscribeRequest;
import org.twins.horn.subscribe.TwinfaceSubscribeProto.TwinfaceSubscribeUpdate;
import org.twins.horn.subscribe.TwinfaceSubscribeServiceGrpc;

import java.io.IOException;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Starts and manages the Twinface gRPC data-streaming service.
 * <p>
 * Responsibilities:
 * <ul>
 *   <li>Creates a {@link io.grpc.Server} on port {@code 9090} backed by a fixed thread pool.</li>
 *   <li>Publishes a {@link TwinfaceSubscribeServiceGrpc.TwinfaceSubscribeServiceImplBase}
 *       implementation that registers clients in {@link ConnectionRegistry} and pushes
 *       {@link TwinfaceSubscribeUpdate} messages to them.</li>
 *   <li>Applies {@link AuthInterceptor} to every call, making OAuth2 token
 *       validation transparent to service logic.</li>
 *   <li>Integrates with {@link TwinfaceGrpcNotifier} and
 *       {@link TwinsNotificationsConsumer} to broadcast RabbitMQ
 *       notifications to connected clients.</li>
 *   <li>Installs a JVM shutdown hook for graceful server termination.</li>
 * </ul>
 * Instantiate the class via Spring and call {@link #start()} to begin serving.
 */
@Service
public class TwinfaceDataStreamingServer {
    private static final Logger logger = LoggerFactory.getLogger(TwinfaceDataStreamingServer.class);

    public Server server;
    private final TwinfaceSubscribeServiceImpl subscribeService;
    private final TwinfaceGrpcNotifier grpcNotifier;
    private final TwinsNotificationsConsumer notificationsConsumer;
    private final AuthInterceptor authInterceptor;

    @Value("${grpc.server.port:9090}")
    private int grpcServerPort;

    public TwinfaceDataStreamingServer(
            TwinfaceGrpcNotifier grpcNotifier,
            TwinsNotificationsConsumer notificationsConsumer,
            AuthInterceptor authInterceptor) {
        this.grpcNotifier = grpcNotifier;
        this.notificationsConsumer = notificationsConsumer;
        this.authInterceptor = authInterceptor;
        this.subscribeService = new TwinfaceSubscribeServiceImpl();
    }

    public void start() throws IOException {
        server = ServerBuilder.forPort(grpcServerPort)
                .executor(Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() * 2))
                .addService(subscribeService)
                .intercept(authInterceptor)
                .build();

        server.start();
        logger.info("gRPC server started on port {}", grpcServerPort);

        // Graceful shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutting down gRPC server...");
            TwinfaceDataStreamingServer.this.stop();
        }));
    }

    private void stop() {
        if (server != null) {
            server.shutdown();
        }
    }

    private static class TwinfaceSubscribeServiceImpl extends TwinfaceSubscribeServiceGrpc.TwinfaceSubscribeServiceImplBase {
        private static final Logger logger = LoggerFactory.getLogger(TwinfaceSubscribeServiceImpl.class);

        @Override
        public void getDataUpdates(TwinfaceSubscribeRequest request,
                                   StreamObserver<TwinfaceSubscribeUpdate> responseObserver) {

            // Register this client to receive notifications

            TokenIntrospectRsDTOv1 tokenInfo = AuthInterceptor.TOKEN_INFO_CTX_KEY.get();
            String clientId = tokenInfo != null ? tokenInfo.getClientId()
                    : request.getClientId();   //todo - throw exception if clientId is not set

            logger.info("Starting data stream for client: {}", clientId);

            // Register this client to receive notifications
            ConnectionRegistry.add(clientId, responseObserver);

            // Optional – clean up when the stream terminates
            ((ServerCallStreamObserver<TwinfaceSubscribeUpdate>)responseObserver).setOnCancelHandler(() ->
                    ConnectionRegistry.remove(clientId, responseObserver));


            // Send initial confirmation
            TwinfaceSubscribeUpdate initialUpdate = TwinfaceSubscribeUpdate.newBuilder()
                    .setUpdateId(UUID.randomUUID().toString())
                    .setTimestamp(Instant.now().toString())
                    .setStatus(TwinfaceSubscribeProto.UpdateStatus.SUCCESS)
                    .setEventType(TwinfaceSubscribeProto.TwinEventType.TWIN_UPDATE)
                    .build();

            try {
                responseObserver.onNext(initialUpdate);
                logger.debug("Sent initial update to client: {}", clientId);
            } catch (Exception e) {
                logger.error("Error sending initial update to client {}: {}", clientId, e.getMessage());
                // Will be handled by onError in client
                responseObserver.onError(e);
            }

            // Client disconnection is handled by the client closing the stream
            // The onCompleted or onError on the client side will trigger cleanup
        }
    }

    // Helper class to manage stream connections
    public static class StreamConnection {
        private final String clientId;
        private final StreamObserver<TwinfaceSubscribeUpdate> responseObserver;
        private final AtomicBoolean active = new AtomicBoolean(true);
        private ScheduledFuture<?> cleanupTask;

        public StreamConnection(String clientId, StreamObserver<TwinfaceSubscribeUpdate> responseObserver) {
            this.clientId = clientId;
            this.responseObserver = responseObserver;
            ConnectionRegistry.add(clientId, responseObserver);
        }

        public void sendUpdate(TwinfaceSubscribeUpdate update) {
            if (active.get()) {
                try {
                    responseObserver.onNext(update);
                } catch (Exception e) {
                    logger.error("Error sending update: {}", e.getMessage());
                    close();
                }
            }
        }

        public void setCleanupTask(ScheduledFuture<?> task) {
            this.cleanupTask = task;
        }

        public void close() {
            if (active.compareAndSet(true, false)) {
                if (cleanupTask != null) {
                    cleanupTask.cancel(true);
                }
                try {
                    responseObserver.onCompleted();
                } catch (Exception e) {
                    logger.error("Error completing stream: {}", e.getMessage());
                }
                ConnectionRegistry.remove(clientId, responseObserver);
            }
        }

        public boolean isActive() {
            return active.get();
        }

        public String getClientId() {
            return clientId;
        }
    }


  /*  @Override
    public void getDataUpdates(TwinfaceSubscribeRequest request, StreamObserver<TwinfaceSubscribeUpdate> responseObserver) {
        // 1. Extract token from metadata
        String token = extractToken(); // TODO: implement extractToken from gRPC context
        if (token == null) {
            responseObserver.onError(Status.UNAUTHENTICATED.withDescription("Missing or invalid token").asRuntimeException());
            return;
        }

        // 2. Validate token
        boolean valid = false; // TODO: Use TwinsTokenIntrospectService to validate token
        if (!valid) {
            responseObserver.onError(Status.UNAUTHENTICATED.withDescription("Token validation failed").asRuntimeException());
            return;
        }

        // 3. Register for notifications
        // TODO: Use TwinsNotificationRequestProducer to subscribe to queue with token and event types

        // 4. Stream updates from queue
        try {
            // TODO: Use TwinsNotificationsConsumer to receive updates and send via responseObserver.onNext(...)
            // TODO: Handle client disconnects, errors, and reconnections
        } catch (Exception e) {
            responseObserver.onError(Status.INTERNAL.withDescription("Streaming error: " + e.getMessage()).asRuntimeException());
        }
    }

    private String extractToken() {
        // TODO: Implement extraction of token from gRPC metadata
        return null;
    }

   */
}
