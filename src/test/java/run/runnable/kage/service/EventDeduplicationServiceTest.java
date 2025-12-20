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

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventDeduplicationServiceTest {

    @Mock
    private ReactiveStringRedisTemplate redisTemplate;
    
    @Mock
    private ReactiveValueOperations<String, String> valueOperations;

    private EventDeduplicationService service;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        service = new EventDeduplicationService(redisTemplate);
    }

    @Test
    @DisplayName("尝试获取锁 - 成功")
    void tryAcquire_success() {
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(Mono.just(true));

        boolean result = service.tryAcquire("test", "123");
        assertTrue(result);
    }

    @Test
    @DisplayName("尝试获取锁 - 失败（已存在）")
    void tryAcquire_fail() {
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(Mono.just(false));

        boolean result = service.tryAcquire("test", "123");
        assertFalse(result);
    }
    
    @Test
    @DisplayName("Redis异常时应默认允许执行")
    void tryAcquire_exception() {
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenThrow(new RuntimeException("Redis connection failed"));

        boolean result = service.tryAcquire("test", "123");
        assertTrue(result);
    }
}
