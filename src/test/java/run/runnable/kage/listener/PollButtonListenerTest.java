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
import run.runnable.kage.domain.Poll;
import run.runnable.kage.service.EventDeduplicationService;
import run.runnable.kage.service.PollService;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import net.dv8tion.jda.api.interactions.components.buttons.ButtonInteraction;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;
import net.dv8tion.jda.api.entities.Message;

@ExtendWith(MockitoExtension.class)
class PollButtonListenerTest {

    @Mock
    private PollService pollService;
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

    private PollButtonListener listener;

    @BeforeEach
    void setUp() {
        listener = new PollButtonListener(pollService, deduplicationService);
    }

    @Test
    @DisplayName("投票 - 成功")
    void onButtonInteraction_voteSuccess() {
        // Mock Event
        when(event.getComponentId()).thenReturn("poll_1_2"); // pollId=1, optionId=2
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
        Poll poll = Poll.builder().id(1L).status(Poll.STATUS_ACTIVE).multipleChoice(false).build();
        when(pollService.findById(1L)).thenReturn(Mono.just(poll));
        when(pollService.vote(1L, 2L, "u1", "user", false)).thenReturn(Mono.just(true));

        listener.onButtonInteraction(event);

        verify(pollService).vote(1L, 2L, "u1", "user", false);
        verify(hook).sendMessage(contains("投票成功"));
    }

    @Test
    @DisplayName("投票 - 格式错误")
    void onButtonInteraction_invalidFormat() {
        when(event.getComponentId()).thenReturn("poll_invalid");
        when(deduplicationService.tryAcquire(anyString(), anyString())).thenReturn(true);
        when(event.getInteraction()).thenReturn(interaction);
        when(interaction.getId()).thenReturn("i1");

        when(event.deferReply(true)).thenReturn(deferAction);
        when(event.getHook()).thenReturn(hook);
        when(hook.sendMessage(anyString())).thenReturn(sendMessageAction);

        listener.onButtonInteraction(event);

        verify(hook).sendMessage(contains("无效的投票"));
        verifyNoInteractions(pollService);
    }
}
