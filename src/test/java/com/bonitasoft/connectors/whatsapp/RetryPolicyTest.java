package com.bonitasoft.connectors.whatsapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class RetryPolicyTest {

    private final RetryPolicy policy = new RetryPolicy() {
        @Override void sleep(long millis) { /* no-op for tests */ }
    };

    @Test
    void should_succeed_on_first_attempt() {
        String result = policy.execute(() -> "ok");
        assertThat(result).isEqualTo("ok");
    }

    @Test
    void should_retry_on_429_and_succeed() {
        AtomicInteger attempts = new AtomicInteger(0);
        String result = policy.execute(() -> {
            if (attempts.incrementAndGet() < 2) {
                throw new WhatsAppException("Rate limit", 429, 130429, true);
            }
            return "recovered";
        });
        assertThat(result).isEqualTo("recovered");
        assertThat(attempts.get()).isEqualTo(2);
    }

    @Test
    void should_retry_on_500_and_succeed() {
        AtomicInteger attempts = new AtomicInteger(0);
        String result = policy.execute(() -> {
            if (attempts.incrementAndGet() < 2) {
                throw new WhatsAppException("Server error", 500, true);
            }
            return "ok";
        });
        assertThat(result).isEqualTo("ok");
    }

    @Test
    void should_not_retry_on_400() {
        assertThatThrownBy(() -> policy.execute(() -> {
            throw new WhatsAppException("Bad request", 400, false);
        })).isInstanceOf(WhatsAppException.class).hasMessageContaining("Bad request");
    }

    @Test
    void should_not_retry_on_131047() {
        assertThatThrownBy(() -> policy.execute(() -> {
            throw new WhatsAppException("24h expired", 400, 131047, false);
        })).isInstanceOf(WhatsAppException.class);
    }

    @Test
    void should_exhaust_retries_and_throw() {
        AtomicInteger attempts = new AtomicInteger(0);
        assertThatThrownBy(() -> policy.execute(() -> {
            attempts.incrementAndGet();
            throw new WhatsAppException("Always fails", 500, true);
        })).isInstanceOf(WhatsAppException.class);
        assertThat(attempts.get()).isEqualTo(4); // initial + 3 retries
    }

    @Test
    void should_calculate_exponential_wait() {
        assertThat(policy.calculateWait(0)).isEqualTo(1000);
        assertThat(policy.calculateWait(1)).isEqualTo(2000);
        assertThat(policy.calculateWait(2)).isEqualTo(4000);
    }

    @Test
    void should_identify_retryable_meta_codes() {
        assertThat(policy.isRetryable(new WhatsAppException("", 429, 130429, true))).isTrue();
        assertThat(policy.isRetryable(new WhatsAppException("", 429, 131056, true))).isTrue();
        assertThat(policy.isRetryable(new WhatsAppException("", 400, 4, false))).isTrue();
        assertThat(policy.isRetryable(new WhatsAppException("", 400, 131047, false))).isFalse();
    }

    @Test
    void should_wrap_unexpected_exceptions() {
        assertThatThrownBy(() -> policy.execute(() -> {
            throw new RuntimeException("boom");
        })).isInstanceOf(WhatsAppException.class).hasMessageContaining("Unexpected error");
    }

    @Test
    void should_retry_on_502() {
        AtomicInteger attempts = new AtomicInteger(0);
        String result = policy.execute(() -> {
            if (attempts.incrementAndGet() < 2) {
                throw new WhatsAppException("Bad Gateway", 502, true);
            }
            return "recovered";
        });
        assertThat(result).isEqualTo("recovered");
        assertThat(attempts.get()).isEqualTo(2);
    }

    @Test
    void should_retry_on_503() {
        AtomicInteger attempts = new AtomicInteger(0);
        String result = policy.execute(() -> {
            if (attempts.incrementAndGet() < 2) {
                throw new WhatsAppException("Service Unavailable", 503, true);
            }
            return "ok";
        });
        assertThat(result).isEqualTo("ok");
    }

    @Test
    void should_not_retry_on_401() {
        assertThatThrownBy(() -> policy.execute(() -> {
            throw new WhatsAppException("Unauthorized", 401, false);
        })).isInstanceOf(WhatsAppException.class).hasMessageContaining("Unauthorized");
    }

    @Test
    void should_not_retry_on_403() {
        assertThatThrownBy(() -> policy.execute(() -> {
            throw new WhatsAppException("Forbidden", 403, false);
        })).isInstanceOf(WhatsAppException.class).hasMessageContaining("Forbidden");
    }

    @Test
    void should_not_retry_on_404() {
        assertThatThrownBy(() -> policy.execute(() -> {
            throw new WhatsAppException("Not Found", 404, false);
        })).isInstanceOf(WhatsAppException.class).hasMessageContaining("Not Found");
    }

    @Test
    void should_retry_on_meta_code_131056() {
        AtomicInteger attempts = new AtomicInteger(0);
        String result = policy.execute(() -> {
            if (attempts.incrementAndGet() < 2) {
                throw new WhatsAppException("Pair rate limit", 429, 131056, true);
            }
            return "ok";
        });
        assertThat(result).isEqualTo("ok");
    }

    @Test
    void should_retry_on_meta_code_4() {
        AtomicInteger attempts = new AtomicInteger(0);
        String result = policy.execute(() -> {
            if (attempts.incrementAndGet() < 2) {
                throw new WhatsAppException("App-level rate limit", 200, 4, false);
            }
            return "ok";
        });
        assertThat(result).isEqualTo("ok");
    }

    @Test
    void should_calculate_exponential_wait_for_higher_attempts() {
        assertThat(policy.calculateWait(3)).isEqualTo(8000);
        assertThat(policy.calculateWait(4)).isEqualTo(16000);
    }

    @Test
    void should_return_null_result_when_callable_returns_null() {
        Object result = policy.execute(() -> null);
        assertThat(result).isNull();
    }

    @Test
    void should_preserve_exception_message_on_non_retryable() {
        assertThatThrownBy(() -> policy.execute(() -> {
            throw new WhatsAppException("Specific error message 12345", 400, false);
        })).isInstanceOf(WhatsAppException.class)
                .hasMessage("Specific error message 12345");
    }

    @Test
    void should_preserve_exception_on_checked_exception() {
        assertThatThrownBy(() -> policy.execute(() -> {
            throw new java.io.IOException("IO error");
        })).isInstanceOf(WhatsAppException.class)
                .hasMessageContaining("Unexpected error")
                .hasMessageContaining("IO error");
    }
}
