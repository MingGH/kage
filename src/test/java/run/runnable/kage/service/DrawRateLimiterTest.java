package run.runnable.kage.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import run.runnable.kage.service.DrawRateLimiter.QuotaExceededException;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DrawRateLimiterTest {

    private ReactiveStringRedisTemplate redisTemplate;
    private ReactiveValueOperations<String, String> valueOps;
    private DrawRateLimiter limiter;

    @BeforeEach
    void setUp() {
        redisTemplate = Mockito.mock(ReactiveStringRedisTemplate.class);
        valueOps = Mockito.mock(ReactiveValueOperations.class);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
        lenient().when(redisTemplate.expire(anyString(), Mockito.any(Duration.class))).thenReturn(Mono.just(true));
        lenient().when(valueOps.decrement(anyString())).thenReturn(Mono.just(0L));
        limiter = new DrawRateLimiter(redisTemplate);
    }

    @Test
    void shouldPassWhenWithinQuota() {
        when(valueOps.increment(contains(":user:u1:"))).thenReturn(Mono.just(1L));
        when(valueOps.increment(contains(":global:"))).thenReturn(Mono.just(50L));

        StepVerifier.create(limiter.tryAcquire("u1")).verifyComplete();
    }

    @Test
    void shouldRejectWhenUserQuotaExceeded() {
        when(valueOps.increment(contains(":user:u1:"))).thenReturn(Mono.just(31L));

        StepVerifier.create(limiter.tryAcquire("u1"))
                .expectErrorSatisfies(e -> {
                    assertThat(e).isInstanceOf(QuotaExceededException.class);
                    assertThat(e.getMessage()).contains("30");
                })
                .verify();

        // 用户超限时不应触碰全局计数
        verify(valueOps, never()).increment(contains(":global:"));
    }

    @Test
    void shouldReturnUserQuotaWhenGlobalExhausted() {
        when(valueOps.increment(contains(":user:u1:"))).thenReturn(Mono.just(1L));
        when(valueOps.increment(contains(":global:"))).thenReturn(Mono.just(101L));

        StepVerifier.create(limiter.tryAcquire("u1"))
                .expectErrorSatisfies(e -> assertThat(e.getMessage()).contains("100"))
                .verify();

        // 全局超限后应归还用户配额与全局占位
        verify(valueOps).decrement(contains(":user:u1:"));
        verify(valueOps).decrement(contains(":global:"));
    }

    @Test
    void shouldDecrementWhenGlobalQuotaExceeded() {
        when(valueOps.increment(contains(":global:"))).thenReturn(Mono.just(101L));

        StepVerifier.create(limiter.tryAcquireGlobal())
                .expectError(QuotaExceededException.class)
                .verify();

        verify(valueOps).decrement(contains(":global:"));
    }

    @Test
    void shouldNotSetTtlAfterFirstIncrement() {
        when(valueOps.increment(contains(":global:"))).thenReturn(Mono.just(7L));

        StepVerifier.create(limiter.tryAcquireGlobal()).verifyComplete();

        verify(redisTemplate, never()).expire(anyString(), Mockito.any(Duration.class));
    }
}
