package run.runnable.kage.listener;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.Interaction;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.requests.restaction.WebhookMessageCreateAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import run.runnable.kage.domain.Lottery;
import run.runnable.kage.domain.LotteryParticipant;
import run.runnable.kage.service.EventDeduplicationService;
import run.runnable.kage.service.LotteryService;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import net.dv8tion.jda.api.interactions.components.buttons.ButtonInteraction;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;
import net.dv8tion.jda.api.entities.Message;

@ExtendWith(MockitoExtension.class)
class LotteryButtonListenerTest {

    @Mock
    private LotteryService lotteryService;
    @Mock
    private EventDeduplicationService deduplicationService;

    @Mock
    private ButtonInteractionEvent event;
    @Mock
    private ButtonInteraction interaction;
    @Mock
    private User user;
    @Mock
    private ReplyCallbackAction deferAction;
    @Mock
    private InteractionHook hook;
    @Mock
    private WebhookMessageCreateAction<Message> sendMessageAction;

    private LotteryButtonListener listener;

    @BeforeEach
    void setUp() {
        listener = new LotteryButtonListener(lotteryService, deduplicationService);
    }

    @Test
    @DisplayName("忽略非抽奖按钮")
    void onButtonInteraction_ignoreOtherButtons() {
        when(event.getComponentId()).thenReturn("other_button");
        listener.onButtonInteraction(event);
        verifyNoInteractions(deduplicationService);
    }

    @Test
    @DisplayName("处理抽奖按钮 - 成功")
    void onButtonInteraction_success() {
        // Mock Event
        when(event.getComponentId()).thenReturn("lottery_join_123");
        when(event.getInteraction()).thenReturn(interaction);
        when(interaction.getId()).thenReturn("i1");
        
        when(event.getUser()).thenReturn(user);
        when(user.getId()).thenReturn("u1");
        when(user.getName()).thenReturn("user");

        // Mock Dedup
        when(deduplicationService.tryAcquire(anyString(), anyString())).thenReturn(true);
        
        // Mock Reply
        when(event.deferReply(true)).thenReturn(deferAction);
        when(event.getHook()).thenReturn(hook);
        when(hook.sendMessage(anyString())).thenReturn(sendMessageAction);

        // Mock Service
        Lottery lottery = Lottery.builder().id(123L).status(Lottery.STATUS_ACTIVE).build();
        when(lotteryService.findById(123L)).thenReturn(Mono.just(lottery));
        when(lotteryService.participate(anyLong(), anyString(), anyString()))
                .thenReturn(Mono.just(LotteryParticipant.builder().build()));
        when(lotteryService.getParticipantCount(123L)).thenReturn(Mono.just(10L));

        listener.onButtonInteraction(event);

        verify(lotteryService).participate(123L, "u1", "user");
        verify(hook).sendMessage(contains("参与成功"));
    }

    @Test
    @DisplayName("处理抽奖按钮 - 活动已结束")
    void onButtonInteraction_ended() {
        // Mock Event
        when(event.getComponentId()).thenReturn("lottery_join_123");
        when(event.getInteraction()).thenReturn(interaction);
        when(interaction.getId()).thenReturn("i1");
        
        when(event.getUser()).thenReturn(user);
        when(user.getId()).thenReturn("u1");
        when(user.getName()).thenReturn("user");

        // Mock Dedup
        when(deduplicationService.tryAcquire(anyString(), anyString())).thenReturn(true);
        
        // Mock Reply
        when(event.deferReply(true)).thenReturn(deferAction);
        when(event.getHook()).thenReturn(hook);
        when(hook.sendMessage(anyString())).thenReturn(sendMessageAction);

        // Mock Service
        Lottery lottery = Lottery.builder().id(123L).status(Lottery.STATUS_ENDED).build();
        when(lotteryService.findById(123L)).thenReturn(Mono.just(lottery));

        listener.onButtonInteraction(event);

        verify(hook).sendMessage(contains("已结束"));
        verify(lotteryService, never()).participate(anyLong(), anyString(), anyString());
    }
}
