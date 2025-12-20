package run.runnable.kage.listener;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.requests.restaction.AuditableRestAction;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import run.runnable.kage.command.CommandManager;
import run.runnable.kage.domain.UserMessage;
import run.runnable.kage.service.LeaderboardStatsService;
import run.runnable.kage.service.MessageQueueService;
import run.runnable.kage.service.MessageRateLimitService;

import java.time.Duration;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DiscordMessageListenerTest {

    @Mock
    private CommandManager commandManager;
    @Mock
    private MessageQueueService messageQueueService;
    @Mock
    private LeaderboardStatsService leaderboardStatsService;
    @Mock
    private MessageRateLimitService messageRateLimitService;

    @Mock
    private MessageReceivedEvent event;
    @Mock
    private User user;
    @Mock
    private Guild guild;
    @Mock
    private MessageChannelUnion channel;
    @Mock
    private Message message;
    @Mock
    private Member member;
    @Mock
    private AuditableRestAction<Void> timeoutAction;
    @Mock
    private MessageCreateAction sendMessageAction;
    @Mock
    private AuditableRestAction<Void> deleteAction;

    private DiscordMessageListener listener;

    @BeforeEach
    void setUp() {
        listener = new DiscordMessageListener(commandManager, messageQueueService, leaderboardStatsService, messageRateLimitService);
    }

    @Test
    @DisplayName("忽略机器人消息")
    void onMessageReceived_ignoreBot() {
        when(event.getAuthor()).thenReturn(user);
        when(user.isBot()).thenReturn(true);

        listener.onMessageReceived(event);

        verifyNoInteractions(commandManager);
    }

    @Test
    @DisplayName("处理正常消息")
    void onMessageReceived_normalMessage() {
        // Setup Mocks
        when(event.getAuthor()).thenReturn(user);
        when(user.isBot()).thenReturn(false);
        when(user.getName()).thenReturn("user");
        when(user.getId()).thenReturn("u1");
        
        when(event.getMessage()).thenReturn(message);
        when(message.getContentRaw()).thenReturn("hello");
        when(event.getMessageId()).thenReturn("m1");
        
        when(event.getChannel()).thenReturn(channel);
        when(channel.getName()).thenReturn("general");
        lenient().when(channel.getId()).thenReturn("c1");
        
        when(event.isFromGuild()).thenReturn(true);
        when(event.getGuild()).thenReturn(guild);
        when(guild.getId()).thenReturn("g1");

        // Mock Rate Limit (Allowed)
        when(messageRateLimitService.recordAndCheck(anyString(), anyString()))
                .thenReturn(Mono.just(MessageRateLimitService.RateLimitResult.ok()));

        // Mock Leaderboard
        when(leaderboardStatsService.recordMessage(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(Mono.empty());

        listener.onMessageReceived(event);

        verify(messageQueueService).pushMessage(any(UserMessage.class));
        verify(leaderboardStatsService).recordMessage(eq("g1"), eq("u1"), eq("user"), eq("hello"));
        verify(commandManager).handleMessage(event);
    }

    @Test
    @DisplayName("触发频率限制")
    void onMessageReceived_rateLimitTriggered() {
        // Setup Mocks
        when(event.getAuthor()).thenReturn(user);
        when(user.isBot()).thenReturn(false);
        when(user.getName()).thenReturn("user");
        when(user.getId()).thenReturn("u1");
        
        when(event.getMessage()).thenReturn(message);
        when(message.getContentRaw()).thenReturn("spam");
        
        when(event.getChannel()).thenReturn(channel);
        when(channel.getName()).thenReturn("general");
        
        when(event.isFromGuild()).thenReturn(true);
        when(event.getGuild()).thenReturn(guild);
        when(guild.getId()).thenReturn("g1");

        // Mock Rate Limit (Triggered)
        MessageRateLimitService.RateLimitResult result = MessageRateLimitService.RateLimitResult.triggered(5);
        when(messageRateLimitService.recordAndCheck(anyString(), anyString()))
                .thenReturn(Mono.just(result));

        // Mock Timeout Actions
        when(event.getMember()).thenReturn(member);
        when(member.timeoutFor(any(Duration.class))).thenReturn(timeoutAction);
        when(timeoutAction.reason(anyString())).thenReturn(timeoutAction);
        
        when(channel.sendMessage(anyString())).thenReturn(sendMessageAction);
        when(message.delete()).thenReturn(deleteAction);

        listener.onMessageReceived(event);

        verify(member).timeoutFor(Duration.ofMinutes(5));
        verify(channel).sendMessage(contains("已被禁言"));
        verify(message).delete();
        verifyNoInteractions(commandManager); // Should not process command
    }
}
