package run.runnable.kage.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * 事件去重服务 - 用于多实例部署时防止重复处理
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EventDeduplicationService {

    private static final String KEY_PREFIX = "discord:event:";
    private static final Duration DEFAULT_EXPIRE = Duration.ofMinutes(5);

    private final ReactiveStringRedisTemplate redisTemplate;

    /**
     * 尝试获取事件处理权
     * @param eventType 事件类型（如 slash, button, message）
     * @param eventId 事件唯一标识
     * @return true 表示获取成功，应该处理；false 表示已被其他实例处理
     */
    public boolean tryAcquire(String eventType, String eventId) {
        return tryAcquire(eventType, eventId, DEFAULT_EXPIRE);
    }

    /**
     * 尝试获取事件处理权
     * @param eventType 事件类型
     * @param eventId 事件唯一标识
     * @param expire 过期时间
     * @return true 表示获取成功
     */
    public boolean tryAcquire(String eventType, String eventId, Duration expire) {
        String key = KEY_PREFIX + eventType + ":" + eventId;
        try {
            Boolean success = redisTemplate.opsForValue()
                    .setIfAbsent(key, "1", expire)
                    .block(Duration.ofSeconds(2));
            return Boolean.TRUE.equals(success);
        } catch (Exception e) {
            log.warn("Redis 去重检查失败，默认允许处理: {}", e.getMessage());
            // Redis 故障时允许处理，避免服务完全不可用
            return true;
        }
    }
}
