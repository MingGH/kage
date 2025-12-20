package run.runnable.kage.command.impl;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import run.runnable.kage.command.CommandContext;
import run.runnable.kage.domain.Lottery;
import run.runnable.kage.service.LotteryService;

import java.time.LocalDateTime;
import java.util.function.Consumer;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LotteryCommandTest {

    @Mock
    private LotteryService lotteryService;
    @Mock
    private CommandContext ctx;
    @Mock
    private Guild guild;
    @Mock
    private User user;
    @Mock
    private MessageChannel channel;
    @Mock
    private CommandContext.ReplyHook hook;
    @Mock
    private MessageCreateAction messageAction;

    private LotteryCommand command;

    @BeforeEach
    void setUp() {
        command = new LotteryCommand(lotteryService);
    }

    @Test
    @DisplayName("非服务器环境应报错")
    void execute_notFromGuild() {
        when(ctx.isFromGuild()).thenReturn(false);
        command.execute(ctx);
        verify(ctx).replyEphemeral(contains("只能在服务器中"));
    }

    @Test
    @DisplayName("参数校验 - 中奖人数错误")
    void execute_invalidWinners() {
        when(ctx.isFromGuild()).thenReturn(true);
        when(ctx.getString("prize")).thenReturn("prize");
        when(ctx.getInteger("winners")).thenReturn(0);
        when(ctx.getInteger("minutes")).thenReturn(10);

        command.execute(ctx);
        verify(ctx).replyEphemeral(contains("中奖人数需要在"));
    }

    @Test
    @DisplayName("Slash命令 - 正常流程")
    void execute_slash_success() {
        when(ctx.isFromGuild()).thenReturn(true);
        when(ctx.getString("prize")).thenReturn("prize");
        when(ctx.getInteger("winners")).thenReturn(1);
        when(ctx.getInteger("minutes")).thenReturn(10);
        
        when(ctx.getGuild()).thenReturn(guild);
        when(ctx.getChannel()).thenReturn(channel);
        when(ctx.getUser()).thenReturn(user);
        when(guild.getId()).thenReturn("g1");
        when(channel.getId()).thenReturn("c1");
        when(user.getId()).thenReturn("u1");

        // Mock deferReply to execute the callback
        doAnswer(invocation -> {
            Consumer<CommandContext.ReplyHook> callback = invocation.getArgument(0);
            callback.accept(hook);
            return null;
        }).when(ctx).deferReply(any());

        // Mock Service
        Lottery lottery = Lottery.builder().id(1L).build();
        when(lotteryService.createLottery(anyString(), anyString(), anyString(), anyString(), anyInt(), any(LocalDateTime.class)))
                .thenReturn(Mono.just(lottery));

        // Mock JDA chain
        when(channel.sendMessage(anyString())).thenReturn(messageAction);
        when(messageAction.setActionRow(any(Button.class))).thenReturn(messageAction);
        
        // Execute
        command.execute(ctx);

        // Verify service called
        verify(lotteryService).createLottery(eq("g1"), eq("c1"), eq("u1"), eq("prize"), eq(1), any(LocalDateTime.class));
        
        // Verify message sent (inside subscribe)
        verify(channel).sendMessage(contains("抽奖活动"));
    }
}
