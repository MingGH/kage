package run.runnable.kage.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import run.runnable.kage.command.CommandRegistry;
import run.runnable.kage.domain.ChatMessage;
import run.runnable.kage.repository.ChatMessageRepository;
import run.runnable.kage.service.tool.ChannelHistoryTool;
import run.runnable.kage.service.tool.CurrentTimeTool;
import run.runnable.kage.service.tool.LeaderboardTool;
import run.runnable.kage.service.tool.RagSearchTool;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 测试 DeepSeekService 流式调用的重试语义（回归测试）。
 * <p>
 * 生产事故背景：某次成语接龙请求在上游流式超时（120s 无输出）后，
 * 重试报 {@code IllegalStateException: No StreamAdvisors available to execute}，
 * 最终 {@code Retries exhausted: 3/3} 静默失败。
 * 根因是 {@code retryWhen} 挂在 {@code chatClient.prompt(prompt).stream()} 之后，
 * 重试只是重新订阅已消费的 Flux（Spring AI 的 advisor chain 一次性），
 * 并没有重新发起请求。修复用 {@code Flux.defer} 包住整段调用，
 * 每次重试都重新执行 {@code chatClient.prompt(...)}。
 * <p>
 * 因此这里的关键断言是：流式失败后重试必须**重新调用 chatClient.prompt**。
 */
@ExtendWith(MockitoExtension.class)
class DeepSeekStreamRetryTest {

    @Mock
    private ChatClient.Builder chatClientBuilder;
    @Mock
    private ChatMessageRepository chatMessageRepository;
    @Mock
    private ChannelHistoryTool channelHistoryTool;
    @Mock
    private CurrentTimeTool currentTimeTool;
    @Mock
    private LeaderboardTool leaderboardTool;
    @Mock
    private RagSearchTool ragSearchTool;
    @Mock
    private ReactiveStringRedisTemplate redisTemplate;
    @Mock
    private ReactiveValueOperations<String, String> valueOperations;
    @Mock
    private CommandRegistry commandRegistry;
    @Mock
    private ChatClient chatClient;

    private DeepSeekService service;

    @BeforeEach
    void setUp() {
        lenient().when(chatClientBuilder.defaultToolCallbacks(any(ToolCallback[].class)))
                .thenReturn(chatClientBuilder);
        lenient().when(chatClientBuilder.defaultTools(any(), any(), any(), any()))
                .thenReturn(chatClientBuilder);
        lenient().when(chatClientBuilder.build()).thenReturn(chatClient);

        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(Mono.just(true));
        lenient().when(redisTemplate.delete(anyString())).thenReturn(Mono.just(1L));
        lenient().when(chatMessageRepository.findRecentByGuildAndUser(anyString(), anyString(), anyInt()))
                .thenReturn(Flux.empty());
        lenient().when(chatMessageRepository.save(any(ChatMessage.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        service = new DeepSeekService(
                chatClientBuilder,
                chatMessageRepository,
                List.of(),
                channelHistoryTool,
                currentTimeTool,
                leaderboardTool,
                ragSearchTool,
                redisTemplate,
                WebClient.builder(),
                "test-system-prompt"
        );
        ReflectionTestUtils.setField(service, "commandRegistry", commandRegistry);
        lenient().when(commandRegistry.getCommandListText()).thenReturn("");
    }

    @Test
    @DisplayName("输出前流式失败：重试应重新调用 chatClient.prompt 并恢复输出")
    void streamFailureBeforeContent_shouldReinvokeChatClientOnRetry() {
        ChatClient.ChatClientRequestSpec firstAttempt =
                mockRequestSpec(Flux.error(new RuntimeException("Stream failed")));
        ChatClient.ChatClientRequestSpec secondAttempt =
                mockRequestSpec(Flux.just(chunk("秋高气爽"), chunk("岁岁平安")));
        when(chatClient.prompt(any(Prompt.class))).thenReturn(firstAttempt, secondAttempt);

        AtomicReference<String> completed = new AtomicReference<>();
        StepVerifier.create(service.chatStream("g1", "u1", "c1", "成语接龙", completed::set))
                .expectNext("秋高气爽", "岁岁平安")
                .verifyComplete();

        // 旧实现只会在组装时调用一次 prompt，重试复用已消费的 Flux 报 No StreamAdvisors
        verify(chatClient, times(2)).prompt(any(Prompt.class));
        assertEquals("秋高气爽岁岁平安", completed.get(), "onComplete 回调应收到完整回复");
    }

    @Test
    @DisplayName("已输出部分内容后失败：不重试，直接降级提示（避免重复回复）")
    void streamFailureAfterPartialContent_shouldNotRetry() {
        ChatClient.ChatClientRequestSpec attempt = mockRequestSpec(Flux.concat(
                Flux.just(chunk("秋高")),
                Flux.error(new RuntimeException("Stream failed"))));
        when(chatClient.prompt(any(Prompt.class))).thenReturn(attempt);

        StepVerifier.create(service.chatStream("g1", "u1", "c1", "成语接龙", content -> { }))
                .expectNext("秋高", "AI 服务暂时不可用，请稍后再试")
                .verifyComplete();

        verify(chatClient, times(1)).prompt(any(Prompt.class));
    }

    @Test
    @DisplayName("重试 3 次仍失败：共调用 4 次（初始 1 + 重试 3），最后降级提示")
    void streamFailureAfterAllRetries_shouldFallBack() {
        when(chatClient.prompt(any(Prompt.class)))
                .thenAnswer(inv -> mockRequestSpec(Flux.error(new RuntimeException("Stream failed"))));

        StepVerifier.create(service.chatStream("g1", "u1", "c1", "成语接龙", content -> { }))
                .expectNext("AI 服务暂时不可用，请稍后再试")
                .verifyComplete();

        verify(chatClient, times(4)).prompt(any(Prompt.class));
    }

    private ChatClient.ChatClientRequestSpec mockRequestSpec(Flux<ChatResponse> responses) {
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.StreamResponseSpec streamSpec = mock(ChatClient.StreamResponseSpec.class);
        when(requestSpec.stream()).thenReturn(streamSpec);
        when(streamSpec.chatResponse()).thenReturn(responses);
        return requestSpec;
    }

    private static ChatResponse chunk(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }
}
