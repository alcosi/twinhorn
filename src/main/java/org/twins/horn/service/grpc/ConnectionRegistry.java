package org.twins.horn.service.grpc;

import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import org.twins.horn.subscribe.TwinfaceSubscribeProto;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Thread-safe utility that maps a client identifier to a list of gRPC
 * {@link StreamObserver StreamObservers} interested in
 * {@link TwinfaceSubscribeProto.TwinfaceSubscribeUpdate} events.
 *
 * <p>Key characteristics:
 * <ul>
 *   <li>Backed by a {@link ConcurrentHashMap} and per-client
 *       {@link CopyOnWriteArrayList} to allow lock-free reads and writes.</li>
 *   <li>Static API – the registry is a singleton; instantiation is prevented by
 *       a private constructor.</li>
 *   <li>Supports three basic operations:
 *     <ul>
 *       <li>{@code add(clientId, observer)} – register a new observer.</li>
 *       <li>{@code remove(clientId, observer)} – deregister an observer and
 *           clean up empty lists.</li>
 *       <li>{@code broadcast(clientId, update)} – push an update to all
 *           observers for the specified client, logging failures but leaving
 *           cleanup to the caller.</li>
 *     </ul>
 *   </li>
 * </ul>
 *
 * <p>All methods are safe to invoke concurrently from multiple threads.
 */
@Slf4j
public class ConnectionRegistry {
    private static final ConcurrentHashMap<String, CopyOnWriteArrayList<StreamObserver<TwinfaceSubscribeProto.TwinfaceSubscribeUpdate>>> observers =
            new ConcurrentHashMap<>();

    private ConnectionRegistry() {
    }

    public static void add(String clientId, StreamObserver<TwinfaceSubscribeProto.TwinfaceSubscribeUpdate> observer) {
        observers.computeIfAbsent(clientId, k -> new CopyOnWriteArrayList<>()).add(observer);
        log.debug("Added observer for client: {}, total observers: {}", clientId,
                observers.getOrDefault(clientId, new CopyOnWriteArrayList<>()).size());
    }

    public static void remove(String clientId, StreamObserver<TwinfaceSubscribeProto.TwinfaceSubscribeUpdate> observer) {
        List<StreamObserver<TwinfaceSubscribeProto.TwinfaceSubscribeUpdate>> list = observers.get(clientId);
        if (list != null) {
            list.remove(observer);
            log.debug("Removed observer for client: {}, remaining observers: {}", clientId, list.size());

            if (list.isEmpty()) {
                observers.remove(clientId);
                log.debug("No more observers for client: {}, removed entry", clientId);
            }
        }
    }

    /**
     * Sends an update to all observers of the given client.
     */
    public static void broadcast(String clientId, TwinfaceSubscribeProto.TwinfaceSubscribeUpdate update) {
        List<StreamObserver<TwinfaceSubscribeProto.TwinfaceSubscribeUpdate>> list = observers.get(clientId);
        if (list != null) {
            log.debug("Broadcasting update to {} observers for client: {}", list.size(), clientId);

            // iterate over snapshot to prevent ConcurrentModification
            int successful = 0;
            for (StreamObserver<TwinfaceSubscribeProto.TwinfaceSubscribeUpdate> obs : list) {
                try {
                    obs.onNext(update);
                    successful++;
                } catch (Exception e) {
                    log.error("Failed to send update to client {}: {}", clientId, e.getMessage());
                    // In a production system, we should remove the failed observer
                    remove(clientId, obs);
                }
            }
            log.debug("Successfully sent updates to {}/{} observers for client: {}",
                    successful, list.size(), clientId);
        } else {
            log.debug("No observers found for client: {}", clientId);
        }
    }
}
