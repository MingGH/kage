package run.runnable.kage.schedule;

import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import run.runnable.kage.domain.UserMessage;
import run.runnable.kage.repository.UserMessageRepository;
import run.runnable.kage.service.DiscordBotService;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Component
public class SensitiveWordScanScheduler {

    private static final String SCHEDULE_LOCK_KEY = "kage:lock:sensitive-word-scan";
    private static final Duration SCHEDULE_LOCK_TTL = Duration.ofMinutes(5);
    private static final int SCAN_WINDOW_MINUTES = 40;
    private static final int MESSAGE_AGE_THRESHOLD_MINUTES = 10;

    private final DiscordBotService discordBotService;
    private final ReactiveStringRedisTemplate redisTemplate;
    private final UserMessageRepository userMessageRepository;
    private final List<String> sensitiveKeywords;

    public SensitiveWordScanScheduler(
            DiscordBotService discordBotService,
            ReactiveStringRedisTemplate redisTemplate,
            UserMessageRepository userMessageRepository,
            @Value("${discord.sensitive-keywords:vpn,梯子}") String keywordsConfig) {
        this.discordBotService = discordBotService;
        this.redisTemplate = redisTemplate;
        this.userMessageRepository = userMessageRepository;
        this.sensitiveKeywords = Arrays.stream(keywordsConfig.split(","))
                .map(String::trim)
                .map(String::toLowerCase)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    @Scheduled(cron = "0 0,30 * * * *")
    public void scanSensitiveWords() {
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(SCHEDULE_LOCK_KEY, "1", SCHEDULE_LOCK_TTL)
                .block();

        if (!Boolean.TRUE.equals(acquired)) {
            return;
        }

        if (!discordBotService.isReady()) {
            log.warn("Discord bot 未就绪，跳过敏感词扫描");
            redisTemplate.delete(SCHEDULE_LOCK_KEY).subscribe();
            return;
        }

        JDA jda = discordBotService.getJda();
        if (jda == null) {
            redisTemplate.delete(SCHEDULE_LOCK_KEY).subscribe();
            return;
        }

        if (sensitiveKeywords.isEmpty()) {
            log.debug("未配置敏感词，跳过扫描");
            redisTemplate.delete(SCHEDULE_LOCK_KEY).subscribe();
            return;
        }

        log.info("开始执行敏感词扫描任务，关键词: {}", sensitiveKeywords);

        LocalDateTime since = LocalDateTime.now().minusMinutes(SCAN_WINDOW_MINUTES);
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(MESSAGE_AGE_THRESHOLD_MINUTES);

        for (Guild guild : jda.getGuilds()) {
            try {
                scanGuild(guild, jda, since, threshold);
            } catch (Exception e) {
                log.error("扫描服务器 {} 失败", guild.getName(), e);
            }
        }

        redisTemplate.delete(SCHEDULE_LOCK_KEY).subscribe();
        log.info("敏感词扫描任务完成");
    }

    private void scanGuild(Guild guild, JDA jda, LocalDateTime since, LocalDateTime threshold) {
        String guildId = guild.getId();

        List<UserMessage> matchedMessages = userMessageRepository
                .findByGuildAndDateRange(guildId, since, LocalDateTime.now())
                .filter(msg -> containsSensitiveWord(msg.getContent()))
                .filter(msg -> msg.getCreatedAt().isBefore(threshold))
                .collectList()
                .block();

        if (matchedMessages == null || matchedMessages.isEmpty()) {
            return;
        }

        log.info("服务器 {} 发现 {} 条敏感消息待删除", guild.getName(), matchedMessages.size());

        for (UserMessage msg : matchedMessages) {
            TextChannel channel = jda.getTextChannelById(msg.getChannelId());
            if (channel == null) {
                log.warn("频道不存在: guild={}, channelId={}", guild.getName(), msg.getChannelId());
                continue;
            }
            channel.deleteMessageById(msg.getMessageId()).queue(
                    v -> log.info("删除敏感消息成功: guild={}, channel={}, user={}, content={}",
                            guild.getName(), channel.getName(), msg.getUserName(),
                            truncate(msg.getContent(), 50)),
                    e -> log.error("删除敏感消息失败: messageId={}, {}", msg.getMessageId(), e.getMessage())
            );
        }
    }

    private boolean containsSensitiveWord(String content) {
        if (content == null) {
            return false;
        }
        String lower = content.toLowerCase();
        for (String keyword : sensitiveKeywords) {
            if (lower.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }
}
