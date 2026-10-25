package run.runnable.kage.schedule;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;
import net.dv8tion.jda.api.requests.restaction.ThreadChannelAction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import reactor.core.publisher.Mono;
import run.runnable.kage.service.DiscordBotService;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WeeklyDiscussionSchedulerTest {

    @Mock
    private DiscordBotService discordBotService;
    @Mock
    private ReactiveStringRedisTemplate redisTemplate;
    @Mock
    private ReactiveValueOperations<String, String> valueOperations;
    @Mock
    private JDA jda;
    @Mock
    private Guild guild;
    @Mock
    private TextChannel channel;
    @Mock
    private MessageCreateAction messageCreateAction;
    @Mock
    private Message parentMessage;
    @Mock
    private ThreadChannelAction threadCreateAction;
    @Mock
    private ThreadChannel thread;

    @Test
    void threadName_usesMondayThroughFridayRange() {
        assertThat(WeeklyDiscussionScheduler.threadName(LocalDate.of(2026, 10, 28)))
                .isEqualTo("本周摸鱼局 · 10.26–10.30");
    }

    @Test
    void prompts_matchWeeklyRhythm() {
        LocalDate date = LocalDate.of(2026, 10, 26);

        assertThat(WeeklyDiscussionScheduler.prompt(DayOfWeek.MONDAY, date))
                .contains("这周准备怎么摸", "周三中午");
        assertThat(WeeklyDiscussionScheduler.prompt(DayOfWeek.WEDNESDAY, date))
                .contains("摸鱼工具", "带上链接");
        assertThat(WeeklyDiscussionScheduler.prompt(DayOfWeek.FRIDAY, date))
                .contains("吐槽", "周末愉快");
    }

    @Test
    void prompt_rejectsUnsupportedDay() {
        assertThatThrownBy(() ->
                WeeklyDiscussionScheduler.prompt(DayOfWeek.TUESDAY, LocalDate.of(2026, 10, 26)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void monday_createsVisibleThreadAndStoresThreadId() {
        Clock clock = Clock.fixed(
                LocalDate.of(2026, 10, 26).atTime(9, 30).atZone(ZoneId.of("Asia/Shanghai")).toInstant(),
                ZoneId.of("Asia/Shanghai"));
        WeeklyDiscussionScheduler scheduler = new WeeklyDiscussionScheduler(
                discordBotService, redisTemplate, true, "guild", "channel", clock);

        when(discordBotService.isReady()).thenReturn(true);
        when(discordBotService.getJda()).thenReturn(jda);
        when(jda.getGuildById("guild")).thenReturn(guild);
        when(guild.getTextChannelById("channel")).thenReturn(channel);
        when(channel.getThreadChannels()).thenReturn(List.of());
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), eq("1"), any())).thenReturn(Mono.just(true));
        when(valueOperations.get(anyString())).thenReturn(Mono.empty());
        when(valueOperations.set(anyString(), eq("thread-1"), any())).thenReturn(Mono.just(true));
        when(channel.sendMessage(anyString())).thenReturn(messageCreateAction);
        when(parentMessage.createThreadChannel(anyString())).thenReturn(threadCreateAction);
        when(threadCreateAction.setAutoArchiveDuration(ThreadChannel.AutoArchiveDuration.TIME_3_DAYS))
                .thenReturn(threadCreateAction);
        when(thread.getId()).thenReturn("thread-1");

        doAnswer(invocation -> {
            Consumer<Message> success = invocation.getArgument(0);
            success.accept(parentMessage);
            return null;
        }).when(messageCreateAction).queue(any(), any());
        doAnswer(invocation -> {
            Consumer<ThreadChannel> success = invocation.getArgument(0);
            success.accept(thread);
            return null;
        }).when(threadCreateAction).queue(any(), any());

        scheduler.postPrompt(DayOfWeek.MONDAY);

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(channel).sendMessage(promptCaptor.capture());
        assertThat(promptCaptor.getValue()).contains("这周准备怎么摸");
        verify(parentMessage).createThreadChannel("本周摸鱼局 · 10.26–10.30");
        verify(threadCreateAction).setAutoArchiveDuration(ThreadChannel.AutoArchiveDuration.TIME_3_DAYS);
        verify(valueOperations).set("kage:weekly-discussion:thread:guild", "thread-1",
                java.time.Duration.ofDays(8));
    }
}
