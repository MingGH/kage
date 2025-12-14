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
import java.time.Instant;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 定时提醒服务
 */
@Slf4j
@Service
public class ReminderService {

    private static final String REMINDER_KEY_PREFIX = "kage:reminder:";
    private static final String SCHEDULE_LOCK_KEY = "kage:lock:reminder-check";
    private static final Duration SCHEDULE_LOCK_TTL = Duration.ofSeconds(8);
    
    // 支持的时间格式: 30s, 5m, 2h, 1d
    private static final Pattern TIME_PATTERN = Pattern.compile("^(\\d+)([smhd])$", Pattern.CASE_INSENSITIVE);
    
    private final ReactiveStringRedisTemplate redisTemplate;
    private final DiscordBotService discordBotService;

    public ReminderService(ReactiveStringRedisTemplate redisTemplate,
                           @Lazy DiscordBotService discordBotService) {
        this.redisTemplate = redisTemplate;
        this.discordBotService = discordBotService;
    }

    /**
     * 设置提醒
     * @param guildId 服务器ID
     * @param channelId 频道ID
     * @param userId 用户ID
     * @param timeStr 时间字符串 (如 30m, 2h)
     * @param message 提醒内容
     * @return 设置结果
     */
    public Mono<String> setReminder(String guildId, String channelId, String userId, String timeStr, String message) {
        Duration duration = parseTime(timeStr);
        if (duration == null) {
            return Mono.just("❌ 时间格式错误，支持: 30s(秒), 5m(分钟), 2h(小时), 1d(天)\n例如: `/remind 30m 喝水`");
        }
        
        if (duration.toSeconds() < 15) {
            return Mono.just("❌ 提醒时间最少 15 秒");
        }
        
        if (duration.toMinutes() > 7 * 24 * 60) {
            return Mono.just("❌ 提醒时间不能超过 7 天");
        }
        
        if (message == null || message.isBlank()) {
            return Mono.just("❌ 请输入提醒内容");
        }
        
        if (message.length() > 200) {
            return Mono.just("❌ 提醒内容不能超过 200 字");
        }
        
        // 计算触发时间戳
        long triggerAt = Instant.now().plus(duration).toEpochMilli();
        
        // 生成唯一 key
        String key = REMINDER_KEY_PREFIX + guildId + ":" + userId + ":" + triggerAt;
        // 存储格式: channelId|message
        String value = channelId + "|" + message;
        
        return redisTemplate.opsForValue()
                .set(key, value, duration.plusMinutes(1)) // 多加1分钟防止边界问题
                .map(success -> {
                    String timeDesc = formatDuration(duration);
                    return String.format("✅ 提醒已设置！\n\n⏰ 将在 **%s** 后提醒你:\n> %s", timeDesc, message);
                });
    }

    /**
     * 解析时间字符串
     */
    private Duration parseTime(String timeStr) {
        if (timeStr == null) return null;
        
        Matcher matcher = TIME_PATTERN.matcher(timeStr.trim());
        if (!matcher.matches()) return null;
        
        long value = Long.parseLong(matcher.group(1));
        String unit = matcher.group(2).toLowerCase();
        
        return switch (unit) {
            case "s" -> Duration.ofSeconds(value);
            case "m" -> Duration.ofMinutes(value);
            case "h" -> Duration.ofHours(value);
            case "d" -> Duration.ofDays(value);
            default -> null;
        };
    }
    
    /**
     * 格式化时间显示
     */
    private String formatDuration(Duration duration) {
        long seconds = duration.getSeconds();
        if (seconds < 60) return seconds + " 秒";
        if (seconds < 3600) return (seconds / 60) + " 分钟";
        if (seconds < 86400) return (seconds / 3600) + " 小时";
        return (seconds / 86400) + " 天";
    }

    /**
     * 每10秒检查一次到期的提醒
     */
    @Scheduled(fixedRate = 10000)  // 每10秒执行
    public void checkReminders() {
        // 尝试获取分布式锁
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(SCHEDULE_LOCK_KEY, "1", SCHEDULE_LOCK_TTL)
                .block();
        
        if (!Boolean.TRUE.equals(acquired)) {
            return;
        }
        
        JDA jda = discordBotService.getJda();
        if (jda == null) {
            redisTemplate.delete(SCHEDULE_LOCK_KEY).subscribe();
            return;
        }
        
        long now = Instant.now().toEpochMilli();
        
        // 扫描所有提醒 key
        redisTemplate.keys(REMINDER_KEY_PREFIX + "*")
                .flatMap(key -> redisTemplate.opsForValue().get(key)
                        .map(value -> new String[]{key, value}))
                .doFinally(signal -> redisTemplate.delete(SCHEDULE_LOCK_KEY).subscribe())
                .subscribe(pair -> {
                    try {
                        String key = pair[0];
                        String value = pair[1];
                        
                        // 解析 key: kage:reminder:guildId:userId:triggerAt
                        String[] keyParts = key.split(":");
                        if (keyParts.length < 5) return;
                        
                        long triggerAt = Long.parseLong(keyParts[4]);
                        
                        // 检查是否到期
                        if (now < triggerAt) return;
                        
                        String userId = keyParts[3];
                        
                        // 解析 value: channelId|message
                        int separatorIndex = value.indexOf('|');
                        if (separatorIndex < 0) return;
                        
                        String channelId = value.substring(0, separatorIndex);
                        String message = value.substring(separatorIndex + 1);
                        
                        // 发送提醒
                        TextChannel channel = jda.getTextChannelById(channelId);
                        if (channel != null) {
                            String reminder = String.format("<@%s> ⏰ **提醒**\n\n%s", userId, message);
                            channel.sendMessage(reminder).queue();
                        }
                        
                        // 删除已触发的提醒
                        redisTemplate.delete(key).subscribe();
                        
                    } catch (Exception e) {
                        log.error("处理提醒失败: {}", e.getMessage());
                    }
                });
    }
}
