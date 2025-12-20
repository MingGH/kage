package run.runnable.kage.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MessageRateLimitServiceTest {

    @Mock
    private ReactiveStringRedisTemplate redisTemplate;
    
    @Mock
    private ReactiveValueOperations<String, String> valueOperations;

    private MessageRateLimitService service;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        service = new MessageRateLimitService(redisTemplate);
    }

    @Test
    @DisplayName("检查禁言状态 - 未禁言")
    void getMuteRemainingSeconds_notMuted() {
        when(redisTemplate.getExpire(anyString())).thenReturn(Mono.empty());
        
        StepVerifier.create(service.getMuteRemainingSeconds("g1", "u1"))
                .expectNext(0L)
                .verifyComplete();
    }

    @Test
    @DisplayName("检查禁言状态 - 已禁言")
    void getMuteRemainingSeconds_muted() {
        when(redisTemplate.getExpire(anyString())).thenReturn(Mono.just(Duration.ofSeconds(60)));
        
        StepVerifier.create(service.getMuteRemainingSeconds("g1", "u1"))
                .expectNext(60L)
                .verifyComplete();
    }

    @Test
    @DisplayName("记录消息 - 正常")
    void recordAndCheck_normal() {
        // Mock not muted
        when(redisTemplate.hasKey(anyString())).thenReturn(Mono.just(false));
        // Mock increment count = 1
        when(valueOperations.increment(anyString())).thenReturn(Mono.just(1L));
        // Mock expire
        when(redisTemplate.expire(anyString(), any(Duration.class))).thenReturn(Mono.just(true));

        StepVerifier.create(service.recordAndCheck("g1", "u1"))
                .expectNextMatches(result -> result.isAllowed() && !result.isTriggered())
                .verifyComplete();
    }
    
    @Test
    @DisplayName("记录消息 - 触发禁言")
    void recordAndCheck_triggerMute() {
        // Mock not muted
        when(redisTemplate.hasKey(anyString())).thenReturn(Mono.just(false));
        // Mock increment count = 46 (Limit is 45)
        when(valueOperations.increment(contains("ratelimit:msg:count"))).thenReturn(Mono.just(46L));
        
        // Mock mute count increment
        when(valueOperations.increment(contains("ratelimit:mute:count"))).thenReturn(Mono.just(1L));
        // Mock mute count expire
        when(redisTemplate.expire(contains("ratelimit:mute:count"), any(Duration.class))).thenReturn(Mono.just(true));
        // Mock set mute key
        when(valueOperations.set(contains("ratelimit:mute:until"), anyString(), any(Duration.class)))
                .thenReturn(Mono.just(true));

        StepVerifier.create(service.recordAndCheck("g1", "u1"))
                .expectNextMatches(result -> !result.isAllowed() && result.isTriggered() && result.getMuteMinutes() == 2)
                .verifyComplete();
    }
    
    @Test
    @DisplayName("记录消息 - 已在禁言中")
    void recordAndCheck_alreadyMuted() {
        // Mock muted
        when(redisTemplate.hasKey(anyString())).thenReturn(Mono.just(true));
        // Mock remaining time
        when(redisTemplate.getExpire(anyString())).thenReturn(Mono.just(Duration.ofSeconds(100)));

        StepVerifier.create(service.recordAndCheck("g1", "u1"))
                .expectNextMatches(result -> !result.isAllowed() && !result.isTriggered() && result.getMuteSeconds() == 100)
                .verifyComplete();
    }

    @Test
    @DisplayName("手动解除禁言")
    void unmute_shouldDeleteKey() {
        when(redisTemplate.delete(anyString())).thenReturn(Mono.just(1L));
        
        StepVerifier.create(service.unmute("g1", "u1"))
                .expectNext(true)
                .verifyComplete();
    }
}
