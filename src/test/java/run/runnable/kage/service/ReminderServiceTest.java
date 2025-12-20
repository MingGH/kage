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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class ReminderServiceTest {

    @Mock
    private ReactiveStringRedisTemplate redisTemplate;
    @Mock
    private DiscordBotService discordBotService;
    @Mock
    private ReactiveValueOperations<String, String> valueOperations;

    private ReminderService service;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        service = new ReminderService(redisTemplate, discordBotService);
    }

    @Test
    @DisplayName("设置提醒 - 格式正确")
    void setReminder_success() {
        when(valueOperations.set(anyString(), anyString(), any(Duration.class)))
                .thenReturn(Mono.just(true));

        StepVerifier.create(service.setReminder("g1", "c1", "u1", "30m", "message"))
                .expectNextMatches(s -> s.contains("✅ 提醒已设置"))
                .verifyComplete();
    }

    @Test
    @DisplayName("设置提醒 - 时间过短")
    void setReminder_tooShort() {
        StepVerifier.create(service.setReminder("g1", "c1", "u1", "10s", "message"))
                .expectNextMatches(s -> s.contains("提醒时间最少 15 秒"))
                .verifyComplete();
    }
    
    @Test
    @DisplayName("设置提醒 - 时间格式错误")
    void setReminder_invalidFormat() {
        StepVerifier.create(service.setReminder("g1", "c1", "u1", "invalid", "message"))
                .expectNextMatches(s -> s.contains("时间格式错误"))
                .verifyComplete();
    }
}
