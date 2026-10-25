package run.runnable.kage.schedule;

import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import run.runnable.kage.service.DiscordBotService;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;

/**
 * 在 #996 创建每周固定的摸鱼讨论帖，并在周三、周五补充话题。
 */
@Slf4j
@Component
public class WeeklyDiscussionScheduler {

    private static final DateTimeFormatter THREAD_DATE_FORMATTER =
            DateTimeFormatter.ofPattern("MM.dd");
    private static final Duration STATE_TTL = Duration.ofDays(8);
    private static final Duration LOCK_TTL = Duration.ofDays(8);
    private static final Duration REDIS_TIMEOUT = Duration.ofSeconds(5);

    private static final String THREAD_KEY_PREFIX = "kage:weekly-discussion:thread:";
    private static final String LOCK_KEY_PREFIX = "kage:weekly-discussion:lock:";
    private static final String THREAD_NAME_PREFIX = "本周摸鱼局 · ";

    private final DiscordBotService discordBotService;
    private final ReactiveStringRedisTemplate redisTemplate;
    private final boolean enabled;
    private final String guildId;
    private final String channelId;
    private final Clock clock;

    @Autowired
    public WeeklyDiscussionScheduler(
            DiscordBotService discordBotService,
            ReactiveStringRedisTemplate redisTemplate,
            @Value("${discord.weekly-discussion.enabled:true}") boolean enabled,
            @Value("${discord.weekly-discussion.guild-id:1235228034865430558}") String guildId,
            @Value("${discord.weekly-discussion.channel-id:1235228034865430561}") String channelId,
            @Value("${discord.weekly-discussion.time-zone:Asia/Shanghai}") String timeZone) {
        this(discordBotService, redisTemplate, enabled, guildId, channelId,
                Clock.system(ZoneId.of(timeZone)));
    }

    WeeklyDiscussionScheduler(
            DiscordBotService discordBotService,
            ReactiveStringRedisTemplate redisTemplate,
            boolean enabled,
            String guildId,
            String channelId,
            Clock clock) {
        this.discordBotService = discordBotService;
        this.redisTemplate = redisTemplate;
        this.enabled = enabled;
        this.guildId = guildId;
        this.channelId = channelId;
        this.clock = clock;
    }

    @Scheduled(
            cron = "${discord.weekly-discussion.monday-cron:0 30 9 * * MON}",
            zone = "${discord.weekly-discussion.time-zone:Asia/Shanghai}")
    public void postMondayPrompt() {
        postPrompt(DayOfWeek.MONDAY);
    }

    @Scheduled(
            cron = "${discord.weekly-discussion.wednesday-cron:0 30 12 * * WED}",
            zone = "${discord.weekly-discussion.time-zone:Asia/Shanghai}")
    public void postWednesdayPrompt() {
        postPrompt(DayOfWeek.WEDNESDAY);
    }

    @Scheduled(
            cron = "${discord.weekly-discussion.friday-cron:0 30 17 * * FRI}",
            zone = "${discord.weekly-discussion.time-zone:Asia/Shanghai}")
    public void postFridayPrompt() {
        postPrompt(DayOfWeek.FRIDAY);
    }

    /**
     * 当天主任务失败后，每 15 分钟重试；成功后的长期锁会阻止重复发送。
     */
    @Scheduled(
            cron = "${discord.weekly-discussion.retry-cron:0 0,15,30,45 9-18 * * MON,WED,FRI}",
            zone = "${discord.weekly-discussion.time-zone:Asia/Shanghai}")
    public void retryTodayPrompt() {
        LocalDate today = LocalDate.now(clock);
        DayOfWeek day = today.getDayOfWeek();
        if ((day != DayOfWeek.MONDAY && day != DayOfWeek.WEDNESDAY && day != DayOfWeek.FRIDAY)
                || LocalTime.now(clock).isBefore(promptTime(day))) {
            return;
        }
        postPrompt(day);
    }

    void postPrompt(DayOfWeek day) {
        if (!enabled) {
            log.debug("每周摸鱼讨论帖已禁用，跳过 {}", day);
            return;
        }
        if (!discordBotService.isReady()) {
            log.warn("Discord bot 未就绪，跳过每周摸鱼讨论帖: {}", day);
            return;
        }

        LocalDate today = LocalDate.now(clock);
        LocalDate weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        if (!acquireEventLock(day, weekStart)) {
            log.debug("每周摸鱼讨论帖 {} 已由其他实例处理", day);
            return;
        }

        JDA jda = discordBotService.getJda();
        Guild guild = jda == null ? null : jda.getGuildById(guildId);
        TextChannel channel = guild == null ? null : guild.getTextChannelById(channelId);
        if (channel == null) {
            log.error("找不到每周摸鱼讨论帖目标频道: guildId={}, channelId={}", guildId, channelId);
            return;
        }

        ThreadChannel existingThread = findThread(jda, channel, weekStart);
        if (existingThread == null) {
            String threadName = threadName(weekStart);
            String prompt = prompt(day, today);
            channel.sendMessage(prompt)
                    .queue(
                            message -> message.createThreadChannel(threadName)
                                    .setAutoArchiveDuration(ThreadChannel.AutoArchiveDuration.TIME_3_DAYS)
                                    .queue(
                                            thread -> {
                                                storeThreadId(thread.getId());
                                                log.info("已创建每周摸鱼讨论帖: {} ({})",
                                                        threadName, thread.getId());
                                            },
                                            error -> {
                                                log.error("从父消息创建每周摸鱼讨论帖失败: {}", threadName, error);
                                                releaseEventLock(day, weekStart);
                                            }
                                    ),
                            error -> {
                                log.error("发送每周摸鱼讨论帖入口消息失败: {}", threadName, error);
                                releaseEventLock(day, weekStart);
                            }
                    );
            return;
        }

        sendPrompt(existingThread, day, weekStart);
    }

    private ThreadChannel findThread(JDA jda, TextChannel channel, LocalDate weekStart) {
        try {
            String storedThreadId = redisTemplate.opsForValue()
                    .get(threadKey())
                    .block(REDIS_TIMEOUT);
            if (storedThreadId != null) {
                ThreadChannel storedThread = jda.getThreadChannelById(storedThreadId);
                if (storedThread != null && isThreadForWeek(storedThread, channel, weekStart)) {
                    return storedThread;
                }
            }
        } catch (Exception e) {
            log.warn("读取每周摸鱼讨论帖状态失败，将尝试从活跃帖子中恢复", e);
        }

        return channel.getThreadChannels().stream()
                .filter(thread -> isThreadForWeek(thread, channel, weekStart))
                .findFirst()
                .orElse(null);
    }

    private boolean isThreadForWeek(ThreadChannel thread, TextChannel channel, LocalDate weekStart) {
        return thread.getParentChannel() != null
                && channel.getId().equals(thread.getParentChannel().getId())
                && threadName(weekStart).equals(thread.getName());
    }

    private void storeThreadId(String threadId) {
        try {
            redisTemplate.opsForValue()
                    .set(threadKey(), threadId, STATE_TTL)
                    .block(REDIS_TIMEOUT);
        } catch (Exception e) {
            log.warn("保存每周摸鱼讨论帖 ID 失败，将依赖活跃帖子恢复", e);
        }
    }

    private boolean acquireEventLock(DayOfWeek day, LocalDate weekStart) {
        try {
            return Boolean.TRUE.equals(redisTemplate.opsForValue()
                    .setIfAbsent(eventLockKey(day, weekStart), "1", LOCK_TTL)
                    .block(REDIS_TIMEOUT));
        } catch (Exception e) {
            log.warn("获取每周摸鱼讨论帖任务锁失败，跳过本次执行", e);
            return false;
        }
    }

    private void releaseEventLock(DayOfWeek day, LocalDate weekStart) {
        redisTemplate.delete(eventLockKey(day, weekStart)).subscribe(
                ignored -> log.info("已释放每周摸鱼讨论帖失败任务锁: day={}", day),
                error -> log.warn("释放每周摸鱼讨论帖任务锁失败: day={}", day, error)
        );
    }

    private void sendPrompt(ThreadChannel thread, DayOfWeek day, LocalDate weekStart) {
        String prompt = prompt(day, LocalDate.now(clock));
        thread.sendMessage(prompt).queue(
                message -> log.info("已发送每周摸鱼讨论帖话题: day={}, threadId={}", day, thread.getId()),
                error -> {
                    log.error("发送每周摸鱼讨论帖话题失败: day={}, threadId={}", day, thread.getId(), error);
                    releaseEventLock(day, weekStart);
                }
        );
    }

    private String threadKey() {
        return THREAD_KEY_PREFIX + guildId;
    }

    private String eventLockKey(DayOfWeek day, LocalDate weekStart) {
        return LOCK_KEY_PREFIX + guildId + ":" + weekStart + ":" + day;
    }

    private LocalTime promptTime(DayOfWeek day) {
        return switch (day) {
            case MONDAY -> LocalTime.of(9, 30);
            case WEDNESDAY -> LocalTime.of(12, 30);
            case FRIDAY -> LocalTime.of(17, 30);
            default -> throw new IllegalArgumentException("Unsupported discussion day: " + day);
        };
    }

    static String threadName(LocalDate anyDayInWeek) {
        LocalDate monday = anyDayInWeek.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate friday = monday.plusDays(4);
        return THREAD_NAME_PREFIX
                + monday.format(THREAD_DATE_FORMATTER)
                + "–"
                + friday.format(THREAD_DATE_FORMATTER);
    }

    static String prompt(DayOfWeek day, LocalDate anyDayInWeek) {
        return switch (day) {
            case MONDAY -> """
                    **本周摸鱼局开始了 👋**

                    新的一周，先来报个到：这周准备怎么摸？

                    1️⃣ 苟住，平安下班
                    2️⃣ 探索一个新工具
                    3️⃣ 偷空推进副业或自学
                    4️⃣ 忙到不配摸鱼 🫠

                    直接回一句就行，不用写小作文。周三中午再来交换工具情报。
                    """.strip();
            case WEDNESDAY -> """
                    **周三工具情报站 🛠️**

                    最近发现了什么值得收藏的摸鱼工具、网站或小技巧？

                    尽量带上链接，再说一句它到底好用在哪。比“马克了”“感谢分享”更容易让后来的人看懂。
                    """.strip();
            case FRIDAY -> """
                    **周五吐槽与复盘 🍻**

                    这周最想吐槽的工作瞬间是什么？有没有哪个摸鱼经验可以留给下周的自己？

                    周末愉快，评论区集合。
                    """.strip();
            default -> throw new IllegalArgumentException("Unsupported discussion day: " + day);
        };
    }
}
