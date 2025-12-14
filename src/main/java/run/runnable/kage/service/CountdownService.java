package run.runnable.kage.service;

import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/**
 * 下班倒计时服务
 */
@Slf4j
@Service
public class CountdownService {

    private static final String COUNTDOWN_KEY_PREFIX = "countdown:";
    private static final String SCHEDULE_LOCK_KEY = "kage:lock:countdown-remind";
    private static final Duration SCHEDULE_LOCK_TTL = Duration.ofMinutes(15);
    private static final ZoneId ZONE_ID = ZoneId.of("Asia/Shanghai");
    
    private final ReactiveStringRedisTemplate redisTemplate;
    private final DiscordBotService discordBotService;

    public CountdownService(ReactiveStringRedisTemplate redisTemplate,
                            @Lazy DiscordBotService discordBotService) {
        this.redisTemplate = redisTemplate;
        this.discordBotService = discordBotService;
    }

    /**
     * 设置下班倒计时
     * @param guildId 服务器ID
     * @param channelId 频道ID
     * @param userId 用户ID
     * @param offWorkTime 下班时间 (HH:mm)
     * @return 设置结果
     */
    public Mono<String> setCountdown(String guildId, String channelId, String userId, String offWorkTime) {
        try {
            LocalTime time = LocalTime.parse(offWorkTime, DateTimeFormatter.ofPattern("HH:mm"));
            LocalDateTime now = LocalDateTime.now(ZONE_ID);
            LocalDateTime offWorkDateTime = LocalDate.now(ZONE_ID).atTime(time);
            
            // 如果设置的时间已经过了，返回错误
            if (offWorkDateTime.isBefore(now)) {
                return Mono.just("❌ 下班时间已过，请设置一个未来的时间");
            }
            
            // 计算到午夜的过期时间
            LocalDateTime midnight = LocalDate.now(ZONE_ID).plusDays(1).atStartOfDay();
            long expireSeconds = ChronoUnit.SECONDS.between(now, midnight);
            
            // 存储格式: channelId:offWorkTime
            String key = COUNTDOWN_KEY_PREFIX + guildId + ":" + userId;
            String value = channelId + ":" + offWorkTime;
            
            return redisTemplate.opsForValue()
                    .set(key, value, Duration.ofSeconds(expireSeconds))
                    .map(success -> {
                        long minutes = ChronoUnit.MINUTES.between(now, offWorkDateTime);
                        long hours = minutes / 60;
                        long mins = minutes % 60;
                        return String.format("✅ 下班倒计时已设置！\n\n🕐 下班时间: %s\n⏰ 距离下班还有 **%d 小时 %d 分钟**\n\n我会每隔 30 分钟提醒你哦~", 
                                offWorkTime, hours, mins);
                    });
        } catch (Exception e) {
            return Mono.just("❌ 时间格式错误，请使用 HH:mm 格式，例如 18:00");
        }
    }

    /**
     * 取消下班倒计时
     */
    public Mono<String> cancelCountdown(String guildId, String userId) {
        String key = COUNTDOWN_KEY_PREFIX + guildId + ":" + userId;
        return redisTemplate.delete(key)
                .map(deleted -> deleted > 0 ? "✅ 下班倒计时已取消" : "❌ 你还没有设置下班倒计时");
    }

    /**
     * 查询当前倒计时状态
     */
    public Mono<String> getCountdownStatus(String guildId, String userId) {
        String key = COUNTDOWN_KEY_PREFIX + guildId + ":" + userId;
        return redisTemplate.opsForValue().get(key)
                .map(value -> {
                    String[] parts = value.split(":");
                    if (parts.length >= 2) {
                        String offWorkTime = parts[1] + ":" + parts[2];
                        return calculateRemaining(offWorkTime);
                    }
                    return "❌ 数据格式错误";
                })
                .defaultIfEmpty("❌ 你还没有设置下班倒计时，使用 /countdown 设置吧~");
    }
    
    private String calculateRemaining(String offWorkTime) {
        try {
            LocalTime time = LocalTime.parse(offWorkTime, DateTimeFormatter.ofPattern("HH:mm"));
            LocalDateTime now = LocalDateTime.now(ZONE_ID);
            LocalDateTime offWorkDateTime = LocalDate.now(ZONE_ID).atTime(time);
            
            if (offWorkDateTime.isBefore(now)) {
                return "🎉 已经下班啦！开心摸鱼去~";
            }
            
            long minutes = ChronoUnit.MINUTES.between(now, offWorkDateTime);
            long hours = minutes / 60;
            long mins = minutes % 60;
            
            return String.format("🕐 下班时间: %s\n⏰ 距离下班还有 **%d 小时 %d 分钟**", offWorkTime, hours, mins);
        } catch (Exception e) {
            return "❌ 时间解析错误";
        }
    }

    /**
     * 每30分钟执行一次，提醒用户下班倒计时
     */
    @Scheduled(cron = "0 0,30 * * * *")  // 每小时的0分和30分执行
    public void remindCountdown() {
        // 尝试获取分布式锁，确保多实例只有一个执行
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(SCHEDULE_LOCK_KEY, "1", SCHEDULE_LOCK_TTL)
                .block();
        
        if (!Boolean.TRUE.equals(acquired)) {
            log.debug("其他实例正在执行下班倒计时提醒，跳过");
            return;
        }
        
        log.info("开始执行下班倒计时提醒...");
        
        JDA jda = discordBotService.getJda();
        if (jda == null) {
            log.warn("JDA 未初始化，跳过提醒");
            redisTemplate.delete(SCHEDULE_LOCK_KEY).subscribe();
            return;
        }
        
        // 扫描所有倒计时 key
        redisTemplate.keys(COUNTDOWN_KEY_PREFIX + "*")
                .flatMap(key -> redisTemplate.opsForValue().get(key)
                        .map(value -> new String[]{key, value}))
                .doFinally(signal -> redisTemplate.delete(SCHEDULE_LOCK_KEY).subscribe())
                .subscribe(pair -> {
                    try {
                        String key = pair[0];
                        String value = pair[1];
                        
                        // 解析 key: countdown:guildId:userId
                        String[] keyParts = key.split(":");
                        if (keyParts.length < 3) return;
                        String userId = keyParts[2];
                        
                        // 解析 value: channelId:HH:mm
                        String[] valueParts = value.split(":");
                        if (valueParts.length < 3) return;
                        
                        String channelId = valueParts[0];
                        String offWorkTime = valueParts[1] + ":" + valueParts[2];
                        
                        // 计算剩余时间
                        LocalTime time = LocalTime.parse(offWorkTime, DateTimeFormatter.ofPattern("HH:mm"));
                        LocalDateTime now = LocalDateTime.now(ZONE_ID);
                        LocalDateTime offWorkDateTime = LocalDate.now(ZONE_ID).atTime(time);
                        
                        if (offWorkDateTime.isBefore(now)) {
                            // 已下班，删除 key
                            redisTemplate.delete(key).subscribe();
                            return;
                        }
                        
                        long minutes = ChronoUnit.MINUTES.between(now, offWorkDateTime);
                        long hours = minutes / 60;
                        long mins = minutes % 60;
                        
                        // 发送提醒，@ 发起倒计时的用户
                        TextChannel channel = jda.getTextChannelById(channelId);
                        if (channel != null) {
                            String message = String.format("<@%s> ⏰ **下班倒计时提醒**\n\n距离下班还有 **%d 小时 %d 分钟**\n\n%s", 
                                    userId, hours, mins, getEncouragement(minutes));
                            channel.sendMessage(message).queue();
                        }
                    } catch (Exception e) {
                        log.error("处理倒计时提醒失败: {}", e.getMessage());
                    }
                });
    }
    
    private String getEncouragement(long minutes) {
        if (minutes <= 30) return "🎉 马上就下班了，再坚持一下！";
        if (minutes <= 60) return "💪 最后一小时，冲鸭！";
        if (minutes <= 120) return "☕ 喝杯咖啡，摸会儿鱼~";
        return "🐟 继续摸鱼，时间会过得很快的~";
    }
}
