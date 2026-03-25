package com.bonitasoft.connectors.whatsapp;

import java.util.Set;
import java.util.concurrent.Callable;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RetryPolicy {

    private static final int MAX_RETRIES = 3;
    private static final long BASE_WAIT_MS = 1_000;
    private static final Set<Integer> RETRYABLE_HTTP = Set.of(429, 500, 502, 503);
    private static final Set<Integer> RETRYABLE_META = Set.of(130429, 131056, 4);

    public <T> T execute(Callable<T> action) throws WhatsAppException {
        WhatsAppException lastException = null;
        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            try {
                return action.call();
            } catch (WhatsAppException e) {
                lastException = e;
                if (!isRetryable(e) || attempt == MAX_RETRIES) {
                    throw e;
                }
                long waitMs = calculateWait(attempt);
                log.warn("Retryable error (attempt {}/{}), waiting {}ms: {}",
                        attempt + 1, MAX_RETRIES, waitMs, e.getMessage());
                sleep(waitMs);
            } catch (Exception e) {
                throw new WhatsAppException("Unexpected error: " + e.getMessage(), e);
            }
        }
        throw lastException;
    }

    boolean isRetryable(WhatsAppException e) {
        return e.isRetryable()
                || RETRYABLE_HTTP.contains(e.getStatusCode())
                || RETRYABLE_META.contains(e.getErrorCode());
    }

    long calculateWait(int attempt) {
        return BASE_WAIT_MS * (1L << attempt);
    }

    void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}
