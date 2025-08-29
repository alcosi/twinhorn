package org.twins.horn.util;

import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.concurrent.Callable;

/**
 * Simple synchronous retry helper that performs exponential back-off with
 * configurable maximum attempts and/or maximum total wait time.  Designed for
 * short blocking operations where the calling thread can afford to wait.
 *
 * The helper also supports optional listeners for retry and exhaustion events
 * so the caller can emit heart-beats or update metrics.
 */
@Slf4j
public class RetryWithBackoffExecutor {

    @FunctionalInterface
    public interface RetryListener {
        void onRetry(int attempt, Exception ex);
    }

    @FunctionalInterface
    public interface ExhaustedListener {
        void onExhausted(Exception ex);
    }

    private final int maxAttempts;
    private final Duration initialDelay;
    private final Duration maxDelay;
    private final double multiplier;
    private final Duration maxTotalWait;

    public RetryWithBackoffExecutor(int maxAttempts,
                                    Duration initialDelay,
                                    Duration maxDelay,
                                    double multiplier,
                                    Duration maxTotalWait) {
        this.maxAttempts = maxAttempts;
        this.initialDelay = initialDelay;
        this.maxDelay = maxDelay;
        this.multiplier = multiplier;
        this.maxTotalWait = maxTotalWait;
    }

    /**
     * Executes the supplied task with retries.
     *
     * @param task          The operation to perform
     * @param onRetry       Callback executed before every retry attempt (1-based)
     * @param onExhausted   Callback executed once retries are exhausted
     */
    public <T> T execute(Callable<T> task,
                         RetryListener onRetry,
                         ExhaustedListener onExhausted) throws Exception {
        Duration delay = initialDelay;
        long waitedMs = 0;
        int attempt = 1;

        while (true) {
            try {
                return task.call();
            } catch (Exception ex) {
                if (attempt >= maxAttempts || waitedMs >= maxTotalWait.toMillis()) {
                    log.error("All retry attempts exhausted ({} attempts, {} ms)", attempt, waitedMs);
                    if (onExhausted != null) {
                        onExhausted.onExhausted(ex);
                    }
                    throw ex; // propagate
                }

                // notify retry listener
                if (onRetry != null) {
                    onRetry.onRetry(attempt, ex);
                }

                log.warn("Transient error on attempt {} – will retry in {} ms: {}",
                        attempt, delay.toMillis(), ex.getMessage());

                Thread.sleep(delay.toMillis());
                waitedMs += delay.toMillis();

                long nextDelayMs = Math.min((long) (delay.toMillis() * multiplier), maxDelay.toMillis());
                delay = Duration.ofMillis(nextDelayMs);
                attempt++;
            }
        }
    }
}
