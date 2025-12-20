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
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class CountdownServiceTest {

    @Mock
    private ReactiveStringRedisTemplate redisTemplate;
    @Mock
    private DiscordBotService discordBotService;
    @Mock
    private ReactiveValueOperations<String, String> valueOperations;

    private CountdownService service;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        service = new CountdownService(redisTemplate, discordBotService);
    }

    @Test
    @DisplayName("设置下班倒计时 - 成功")
    void setCountdown_success() {
        // Construct a future time (e.g. 23:59) to ensure it's valid for today
        // Note: This might fail if run exactly at 23:59:59, but low probability.
        // A safer bet is to just use a time that is definitely in the future for today if possible,
        // or mock LocalDateTime.now() but the service uses static method.
        // Let's assume testing during the day. If it's late night, we might need to be careful.
        // We can just use "23:59".
        
        when(valueOperations.set(anyString(), anyString(), any(Duration.class)))
                .thenReturn(Mono.just(true));

        StepVerifier.create(service.setCountdown("g1", "c1", "u1", "23:59"))
                .expectNextMatches(s -> s.contains("✅ 下班倒计时已设置"))
                .verifyComplete();
    }

    @Test
    @DisplayName("取消倒计时")
    void cancelCountdown_success() {
        when(redisTemplate.delete(anyString())).thenReturn(Mono.just(1L));

        StepVerifier.create(service.cancelCountdown("g1", "u1"))
                .expectNextMatches(s -> s.contains("✅ 下班倒计时已取消"))
                .verifyComplete();
    }

    @Test
    @DisplayName("获取倒计时状态 - 存在")
    void getCountdownStatus_exists() {
        // Mock existing value "channelId:23:59"
        when(valueOperations.get(anyString())).thenReturn(Mono.just("c1:23:59"));

        StepVerifier.create(service.getCountdownStatus("g1", "u1"))
                .expectNextMatches(s -> s.contains("距离下班还有"))
                .verifyComplete();
    }
    
    @Test
    @DisplayName("获取倒计时状态 - 不存在")
    void getCountdownStatus_notExists() {
        when(valueOperations.get(anyString())).thenReturn(Mono.empty());

        StepVerifier.create(service.getCountdownStatus("g1", "u1"))
                .expectNextMatches(s -> s.contains("你还没有设置下班倒计时"))
                .verifyComplete();
    }
}
