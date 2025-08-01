package org.twins.horn.service.grpc;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

import java.util.concurrent.TimeUnit;

/**
 * Manages a single gRPC {@link ManagedChannel} configured for client-side use.
 *
 * Features:
 *  • Builds a channel for the supplied target with 30 s keep-alive pings and plaintext transport.
 *  • Stores the channel in a final field for safe, application-wide reuse.
 *  • Provides a hint for future tuning through {@code maxConcurrentStreams}.
 *
 * Usage:
 *  ChannelManager mgr = new ChannelManager("host:port");
 *  ManagedChannel ch = mgr.getChannel();
 *
 * No explicit shutdown is performed here; callers are responsible for invoking
 * {@code ManagedChannel#shutdown()} or {@code ManagedChannel#shutdownNow()} when finished.
 */
public class ChannelManager {
    private final ManagedChannel channel;
    private final int maxConcurrentStreams = 100;

    public ChannelManager(String target) {
        this.channel = ManagedChannelBuilder.forTarget(target)
                .keepAliveTime(30, TimeUnit.SECONDS)
                .keepAliveWithoutCalls(true)
                // maxConcurrentCallsPerConnection is server-side only; removed for client builder
                .usePlaintext()
                .build();
    }

    // Implement connection pooling if needed
    public ManagedChannel getChannel() {
        return channel;
    }
}
