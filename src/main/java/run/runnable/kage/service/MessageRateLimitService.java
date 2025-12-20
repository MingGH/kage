package run.runnable.kage.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 消息频率限制服务
 * 规则：1分钟内超过60条消息触发禁言
 * 禁言时间使用斐波那契数列递增：2, 3, 5, 8, 13, 21, 34, 55, 89... 分钟
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MessageRateLimitService {

    private static final String MSG_COUNT_KEY_PREFIX = "ratelimit:msg:count:";
    private static final String MUTE_COUNT_KEY_PREFIX = "ratelimit:mute:count:";
    private static final String MUTE_UNTIL_KEY_PREFIX = "ratelimit:mute:until:";
    
    private static final int MAX_MESSAGES_PER_MINUTE = 45;
    private static final Duration COUNT_WINDOW = Duration.ofMinutes(1);
    private static final Duration MUTE_COUNT_EXPIRE = Duration.ofHours(24); // 24小时后重置违规次数
    
    // 斐波那契数列禁言时间（分钟）
    private static final List<Integer> FIBONACCI_MUTE_MINUTES = Arrays.asList(
            2, 3, 5, 8, 13, 21, 34, 55, 89, 144, 233, 377, 610
    );

    private final ReactiveStringRedisTemplate redisTemplate;

    /**
     * 检查用户是否被禁言
     * @param guildId 服务器ID
     * @param userId 用户ID
     * @return 剩余禁言秒数，0表示未被禁言
     */
    public Mono<Long> getMuteRemainingSeconds(String guildId, String userId) {
        String muteKey = MUTE_UNTIL_KEY_PREFIX + guildId + ":" + userId;
        return redisTemplate.getExpire(muteKey)
                .map(duration -> duration.getSeconds())
                .defaultIfEmpty(0L)
                .onErrorReturn(0L);
    }


    /**
     * 记录消息并检查是否需要禁言
     * @return true 表示消息正常，false 表示触发限制
     */
    public Mono<RateLimitResult> recordAndCheck(String guildId, String userId) {
        String countKey = MSG_COUNT_KEY_PREFIX + guildId + ":" + userId;
        String muteKey = MUTE_UNTIL_KEY_PREFIX + guildId + ":" + userId;
        
        // 先检查是否已被禁言
        return redisTemplate.hasKey(muteKey)
                .flatMap(isMuted -> {
                    if (Boolean.TRUE.equals(isMuted)) {
                        return getMuteRemainingSeconds(guildId, userId)
                                .map(seconds -> RateLimitResult.muted(seconds));
                    }
                    // 增加消息计数
                    return incrementAndCheck(guildId, userId, countKey);
                })
                .onErrorResume(e -> {
                    log.error("频率限制检查失败: {}", e.getMessage());
                    return Mono.just(RateLimitResult.ok());
                });
    }

    private Mono<RateLimitResult> incrementAndCheck(String guildId, String userId, String countKey) {
        return redisTemplate.opsForValue().increment(countKey)
                .flatMap(count -> {
                    // 首次设置过期时间
                    if (count == 1) {
                        return redisTemplate.expire(countKey, COUNT_WINDOW)
                                .thenReturn(RateLimitResult.ok());
                    }
                    // 检查是否超限
                    if (count > MAX_MESSAGES_PER_MINUTE) {
                        return applyMute(guildId, userId)
                                .map(muteMinutes -> RateLimitResult.triggered(muteMinutes));
                    }
                    return Mono.just(RateLimitResult.ok());
                });
    }

    /**
     * 应用禁言
     * @return 禁言分钟数
     */
    private Mono<Integer> applyMute(String guildId, String userId) {
        String muteCountKey = MUTE_COUNT_KEY_PREFIX + guildId + ":" + userId;
        String muteKey = MUTE_UNTIL_KEY_PREFIX + guildId + ":" + userId;
        
        return redisTemplate.opsForValue().increment(muteCountKey)
                .flatMap(muteCount -> {
                    // 首次设置违规计数过期时间
                    if (muteCount == 1) {
                        redisTemplate.expire(muteCountKey, MUTE_COUNT_EXPIRE).subscribe();
                    }
                    
                    // 获取斐波那契禁言时间
                    int index = Math.min(muteCount.intValue() - 1, FIBONACCI_MUTE_MINUTES.size() - 1);
                    int muteMinutes = FIBONACCI_MUTE_MINUTES.get(index);
                    
                    log.info("用户触发禁言 - guildId: {}, userId: {}, 第{}次违规, 禁言{}分钟",
                            guildId, userId, muteCount, muteMinutes);
                    
                    // 设置禁言标记
                    return redisTemplate.opsForValue()
                            .set(muteKey, String.valueOf(muteMinutes), Duration.ofMinutes(muteMinutes))
                            .thenReturn(muteMinutes);
                });
    }

    /**
     * 手动解除禁言
     */
    public Mono<Boolean> unmute(String guildId, String userId) {
        String muteKey = MUTE_UNTIL_KEY_PREFIX + guildId + ":" + userId;
        return redisTemplate.delete(muteKey)
                .map(count -> count > 0)
                .doOnSuccess(success -> {
                    if (Boolean.TRUE.equals(success)) {
                        log.info("手动解除禁言 - guildId: {}, userId: {}", guildId, userId);
                    }
                });
    }

    /**
     * 重置用户的违规次数
     */
    public Mono<Boolean> resetMuteCount(String guildId, String userId) {
        String muteCountKey = MUTE_COUNT_KEY_PREFIX + guildId + ":" + userId;
        return redisTemplate.delete(muteCountKey)
                .map(count -> count > 0);
    }

    /**
     * 获取用户当前违规次数
     */
    public Mono<Integer> getMuteCount(String guildId, String userId) {
        String muteCountKey = MUTE_COUNT_KEY_PREFIX + guildId + ":" + userId;
        return redisTemplate.opsForValue().get(muteCountKey)
                .map(Integer::parseInt)
                .defaultIfEmpty(0);
    }

    /**
     * 频率限制检查结果
     */
    public static class RateLimitResult {
        private final boolean allowed;
        private final boolean triggered;
        private final long muteSeconds;
        private final int muteMinutes;

        private RateLimitResult(boolean allowed, boolean triggered, long muteSeconds, int muteMinutes) {
            this.allowed = allowed;
            this.triggered = triggered;
            this.muteSeconds = muteSeconds;
            this.muteMinutes = muteMinutes;
        }

        public static RateLimitResult ok() {
            return new RateLimitResult(true, false, 0, 0);
        }

        public static RateLimitResult muted(long remainingSeconds) {
            return new RateLimitResult(false, false, remainingSeconds, 0);
        }

        public static RateLimitResult triggered(int muteMinutes) {
            return new RateLimitResult(false, true, muteMinutes * 60L, muteMinutes);
        }

        public boolean isAllowed() { return allowed; }
        public boolean isTriggered() { return triggered; }
        public long getMuteSeconds() { return muteSeconds; }
        public int getMuteMinutes() { return muteMinutes; }
        
        public String getFormattedMuteTime() {
            if (muteSeconds <= 0) return "";
            long minutes = muteSeconds / 60;
            long seconds = muteSeconds % 60;
            if (minutes > 0) {
                return seconds > 0 ? minutes + "分" + seconds + "秒" : minutes + "分钟";
            }
            return seconds + "秒";
        }
    }
}
