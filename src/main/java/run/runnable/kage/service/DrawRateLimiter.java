package run.runnable.kage.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * 画图配额限流器（Redis 日计数）
 * 全局 100 张/天，单用户 30 张/天，按 Asia/Shanghai 日期重置
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DrawRateLimiter {

    private static final int GLOBAL_DAILY_LIMIT = 100;
    private static final int USER_DAILY_LIMIT = 30;
    private static final Duration KEY_TTL = Duration.ofDays(2);
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZONE);
    private static final String GLOBAL_KEY_PREFIX = "kage:draw:quota:global:";
    private static final String USER_KEY_PREFIX = "kage:draw:quota:user:";
    private static final String GLOBAL_REJECT_MSG = "今日全局限额已用完（100 张/天），明天再来吧~";
    private static final String USER_REJECT_MSG = "你今天的画图额度用完啦（30 张/天），明天再来吧~";

    private final ReactiveStringRedisTemplate redisTemplate;

    /**
     * 占用用户+全局配额；任一超限则抛 {@link QuotaExceededException}，失败时归还已占用的配额
     */
    public Mono<Void> tryAcquire(String userId) {
        String day = DAY.format(LocalDate.now(ZONE));
        String userKey = USER_KEY_PREFIX + userId + ":" + day;
        // defer 保证用户配额通过后才组装全局检查（惰性组装，避免无效计数路径）
        return consume(userKey, USER_DAILY_LIMIT, USER_REJECT_MSG)
                .then(Mono.defer(() -> consume(globalKey(day), GLOBAL_DAILY_LIMIT, GLOBAL_REJECT_MSG)
                        // 全局超限时归还用户配额，避免白扣
                        .onErrorResume(e -> returnQuotaQuietly(userKey).then(Mono.error(e)))));
    }

    /**
     * 仅占用全局配额（HTTP 测试接口用）
     */
    public Mono<Void> tryAcquireGlobal() {
        return consume(globalKey(DAY.format(LocalDate.now(ZONE))), GLOBAL_DAILY_LIMIT, GLOBAL_REJECT_MSG);
    }

    /**
     * INCR 占位并校验上限，超限则 DECR 归还后报错，保证计数只反映成功占用
     */
    private Mono<Void> consume(String key, int limit, String rejectMessage) {
        return redisTemplate.opsForValue()
                .increment(key)
                .flatMap(count -> setTtlOnFirst(key, count)
                        .onErrorResume(e -> returnQuotaQuietly(key).then(Mono.error(e)))
                        .thenReturn(count))
                .flatMap(count -> count <= limit
                        ? Mono.empty()
                        : returnQuotaQuietly(key)
                                .then(Mono.error(new QuotaExceededException(rejectMessage))));
    }

    private Mono<Boolean> setTtlOnFirst(String key, long count) {
        return count == 1 ? redisTemplate.expire(key, KEY_TTL) : Mono.just(true);
    }

    /**
     * 归还配额；归还失败只告警，不顶替业务异常
     */
    private Mono<Long> returnQuotaQuietly(String key) {
        return redisTemplate.opsForValue()
                .decrement(key)
                .onErrorResume(e -> {
                    log.warn("归还配额失败: {}", key);
                    return Mono.empty();
                });
    }

    private String globalKey(String day) {
        return GLOBAL_KEY_PREFIX + day;
    }

    /**
     * 配额超限异常，message 直接展示给用户
     */
    public static class QuotaExceededException extends RuntimeException {
        public QuotaExceededException(String message) {
            super(message);
        }
    }
}
