package run.runnable.kage.service;

import io.modelcontextprotocol.client.McpAsyncClient;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.mcp.AsyncMcpToolCallback;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import run.runnable.kage.command.CommandRegistry;
import run.runnable.kage.domain.ChatMessage;
import run.runnable.kage.repository.ChatMessageRepository;

import org.springframework.beans.factory.annotation.Value;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

@Slf4j
@Service
public class DeepSeekService {

    private static final int MAX_HISTORY_SIZE = 20;

    private final ChatClient chatClient;
    private final ChatMessageRepository chatMessageRepository;
    private final CommandRegistry commandRegistry;
    private final ToolCallback[] mcpTools;
    private final String systemPromptTemplate;

    public DeepSeekService(ChatClient.Builder chatClientBuilder,
                           ChatMessageRepository chatMessageRepository,
                           @Lazy CommandRegistry commandRegistry,
                           @Lazy McpAsyncClient mcpAsyncClient,
                           @Value("${ai.system-prompt}") String systemPromptTemplate) {
        this.systemPromptTemplate = systemPromptTemplate;
        this.chatMessageRepository = chatMessageRepository;
        this.commandRegistry = commandRegistry;
        
        // 从自定义 MCP Client 获取工具
        var tools = mcpAsyncClient.listTools().block();
        if (tools != null && tools.tools() != null) {
            this.mcpTools = tools.tools().stream()
                    .map(tool -> new AsyncMcpToolCallback(mcpAsyncClient, tool))
                    .toArray(ToolCallback[]::new);
            log.info("已加载 {} 个 MCP 工具", mcpTools.length);
            for (ToolCallback tool : mcpTools) {
                log.info("  - {}: {}", tool.getToolDefinition().name(), tool.getToolDefinition().description());
            }
        } else {
            this.mcpTools = new ToolCallback[0];
            log.warn("未能加载 MCP 工具");
        }
        
        // 构建带工具的 ChatClient
        this.chatClient = chatClientBuilder
                .defaultToolCallbacks(mcpTools)
                .build();
    }

    private String getSystemPrompt() {
        String currentTime = LocalDateTime.now(ZoneId.of("Asia/Shanghai"))
                .format(DateTimeFormatter.ofPattern("yyyy年M月d日 EEEE HH:mm", Locale.CHINESE));
        return systemPromptTemplate
                .replace("{time}", currentTime)
                .replace("{commands}", commandRegistry.getCommandListText());
    }

    /**
     * 与 AI 进行对话
     */
    public Mono<String> chat(String guildId, String userId, String userMessage) {
        return loadChatHistory(guildId, userId)
                .flatMap(history -> callAi(history, userMessage))
                .flatMap(content -> saveAndReturn(guildId, userId, userMessage, content))
                .doOnError(e -> log.error("AI 调用失败: {}", e.getMessage()))
                .onErrorReturn("AI 服务暂时不可用，请稍后再试");
    }

    /**
     * 加载对话历史
     */
    private Mono<List<ChatMessage>> loadChatHistory(String guildId, String userId) {
        return chatMessageRepository.findRecentByGuildAndUser(guildId, userId, MAX_HISTORY_SIZE)
                .collectList()
                .map(history -> {
                    Collections.reverse(history);
                    return history;
                });
    }

    /**
     * 构建消息列表并调用 AI
     */
    private Mono<String> callAi(List<ChatMessage> history, String userMessage) {
        List<Message> messages = buildMessages(history, userMessage);
        
        return Mono.fromCallable(() -> {
            log.info("开始调用 AI，消息数: {}, 可用工具数: {}", messages.size(), mcpTools.length);
            Prompt prompt = new Prompt(messages);
            var response = chatClient.prompt(prompt).call();
            String content = response.content();
            log.info("AI 响应完成，内容长度: {}", content != null ? content.length() : 0);
            return content;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 构建 Spring AI 消息列表
     */
    private List<Message> buildMessages(List<ChatMessage> history, String userMessage) {
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(getSystemPrompt()));

        history.forEach(msg -> {
            if ("user".equals(msg.getRole())) {
                messages.add(new UserMessage(msg.getContent()));
            } else if ("assistant".equals(msg.getRole())) {
                messages.add(new AssistantMessage(msg.getContent()));
            }
        });

        messages.add(new UserMessage(userMessage));
        return messages;
    }

    /**
     * 保存对话历史并返回响应
     */
    private Mono<String> saveAndReturn(String guildId, String userId, String userMessage, String content) {
        saveChatHistory(guildId, userId, userMessage, content);
        return Mono.just(content);
    }

    /**
     * 异步保存对话历史
     */
    private void saveChatHistory(String guildId, String userId, String userMessage, String assistantContent) {
        LocalDateTime now = LocalDateTime.now();
        
        ChatMessage userMsg = ChatMessage.builder()
                .guildId(guildId)
                .userId(userId)
                .role("user")
                .content(userMessage)
                .deleted(false)
                .createdAt(now)
                .build();
        
        ChatMessage assistantMsg = ChatMessage.builder()
                .guildId(guildId)
                .userId(userId)
                .role("assistant")
                .content(assistantContent)
                .deleted(false)
                .createdAt(now.plusNanos(1000))
                .build();

        chatMessageRepository.save(userMsg)
                .then(chatMessageRepository.save(assistantMsg))
                .subscribe(
                        v -> {},
                        e -> log.error("保存对话历史失败: {}", e.getMessage())
                );
    }

    public Mono<Void> clearHistory(String guildId, String userId) {
        return chatMessageRepository.softDeleteByGuildAndUser(guildId, userId);
    }

}
