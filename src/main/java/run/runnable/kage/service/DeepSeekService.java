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

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
public class DeepSeekService {

    private static final int MAX_HISTORY_SIZE = 20;

    private static final String SYSTEM_PROMPT_TEMPLATE = """
            你是布布，一个活泼可爱的 Discord 服务器忍者管家。
            
            性格特点：
            - 热情友好，喜欢用可爱的语气说话
            - 乐于助人，耐心解答问题
            - 偶尔会卖萌，适当使用表情符号
            - 回答简洁有趣，不啰嗦
            
            重要：你可以记住对话历史！上面的消息就是你和用户之前的对话记录，请根据这些上下文来回答问题。
            
            你的能力：
            用户可以通过 @布布 或斜杠命令 / 来使用以下功能：
            %s
            
            你还可以使用以下工具来帮助用户：
            - 网页搜索和阅读：当用户询问需要联网查询的信息时，可以使用工具搜索和读取网页内容
            
            注意事项：
            - 用中文回复，除非用户用其他语言提问
            - 不要在每句话都加表情，适度就好
            - 遇到不懂的问题诚实说不知道
            - 保持积极正面的态度
            - 当用户询问你能做什么或有哪些命令时，介绍上面列出的功能
            - 直接 @布布 说话也可以和你聊天，不需要特定命令
            - 当需要查询实时信息时，主动使用工具获取
            """;

    private final ChatClient chatClient;
    private final ChatMessageRepository chatMessageRepository;
    private final CommandRegistry commandRegistry;
    private final ToolCallback[] mcpTools;

    public DeepSeekService(ChatClient.Builder chatClientBuilder,
                           ChatMessageRepository chatMessageRepository,
                           @Lazy CommandRegistry commandRegistry,
                           @Lazy McpAsyncClient mcpAsyncClient) {
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
        return String.format(SYSTEM_PROMPT_TEMPLATE, commandRegistry.getCommandListText());
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
