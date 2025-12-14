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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;
import run.runnable.kage.command.CommandRegistry;
import run.runnable.kage.domain.ChatMessage;
import run.runnable.kage.repository.ChatMessageRepository;
import run.runnable.kage.service.tool.ChannelHistoryTool;

import org.springframework.beans.factory.annotation.Value;

import org.springframework.data.redis.core.ReactiveStringRedisTemplate;

import java.time.Duration;
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
    private static final String PROCESSING_KEY_PREFIX = "kage:processing:";
    private static final Duration PROCESSING_LOCK_TTL = Duration.ofMinutes(5);
    
    private final ReactiveStringRedisTemplate redisTemplate;
    private final ChatClient chatClient;
    private final ChatMessageRepository chatMessageRepository;
    private final ToolCallback[] allTools;
    private final String systemPromptTemplate;
    private final String mcpToolsDescription;
    private final ChannelHistoryTool channelHistoryTool;
    
    @Lazy
    @Autowired
    private CommandRegistry commandRegistry;

    public DeepSeekService(ChatClient.Builder chatClientBuilder,
                           ChatMessageRepository chatMessageRepository,
                           @Lazy McpAsyncClient mcpAsyncClient,
                           ChannelHistoryTool channelHistoryTool,
                           ReactiveStringRedisTemplate redisTemplate,
                           @Value("${ai.system-prompt}") String systemPromptTemplate) {
        this.systemPromptTemplate = systemPromptTemplate;
        this.chatMessageRepository = chatMessageRepository;
        this.channelHistoryTool = channelHistoryTool;
        this.redisTemplate = redisTemplate;
        
        List<ToolCallback> toolList = new ArrayList<>();
        StringBuilder toolDescBuilder = new StringBuilder();
        
        // 从自定义 MCP Client 获取工具
        var tools = mcpAsyncClient.listTools().block();
        if (tools != null && tools.tools() != null) {
            var mcpTools = tools.tools().stream()
                    .map(tool -> new AsyncMcpToolCallback(mcpAsyncClient, tool))
                    .toList();
            toolList.addAll(mcpTools);
            log.info("已加载 {} 个 MCP 工具", mcpTools.size());
            
            // 构建 MCP 工具描述
            for (ToolCallback tool : mcpTools) {
                String name = tool.getToolDefinition().name();
                String desc = tool.getToolDefinition().description();
                // 简化工具名（去掉 k_b_ 前缀）
                String simpleName = name.startsWith("k_b_") ? name.substring(4) : name;
                toolDescBuilder.append("- ").append(simpleName).append(": ").append(getShortDescription(desc)).append("\n");
                log.info("  - {}: {}", name, desc);
            }
        } else {
            log.warn("未能加载 MCP 工具");
        }
        
        // 添加内置工具描述
        toolDescBuilder.append("- getRecentChannelMessages: 查询当前频道最近的聊天记录\n");
        
        this.mcpToolsDescription = toolDescBuilder.length() > 0 ? toolDescBuilder.toString() : "暂无可用工具";
        this.allTools = toolList.toArray(new ToolCallback[0]);
        
        // 构建带工具的 ChatClient（内置工具通过 @Tool 注解自动注册）
        this.chatClient = chatClientBuilder
                .defaultToolCallbacks(allTools)
                .defaultTools(channelHistoryTool)
                .build();
    }

    @PostConstruct
    public void init() {
        log.info("========== AI 系统提示词 ==========");
        log.info("\n{}", getSystemPrompt());
        log.info("===================================");
    }
    
    /**
     * 获取简短描述（取第一句话）
     */
    private String getShortDescription(String desc) {
        if (desc == null) return "";
        int idx = desc.indexOf('.');
        if (idx > 0 && idx < 80) {
            return desc.substring(0, idx + 1);
        }
        return desc.length() > 80 ? desc.substring(0, 80) + "..." : desc;
    }

    private String getSystemPrompt() {
        String currentTime = LocalDateTime.now(ZoneId.of("Asia/Shanghai"))
                .format(DateTimeFormatter.ofPattern("yyyy年M月d日 EEEE HH:mm", Locale.CHINESE));
        return systemPromptTemplate
                .replace("{time}", currentTime)
                .replace("{commands}", commandRegistry.getCommandListText())
                .replace("{tools}", mcpToolsDescription);
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
     * 检查用户是否正在处理中
     */
    public boolean isUserProcessing(String guildId, String userId) {
        String redisKey = PROCESSING_KEY_PREFIX + guildId + ":" + userId;
        return Boolean.TRUE.equals(redisTemplate.hasKey(redisKey).block());
    }
    
    /**
     * 尝试获取用户处理锁
     */
    private boolean tryAcquireLock(String guildId, String userId) {
        String redisKey = PROCESSING_KEY_PREFIX + guildId + ":" + userId;
        return Boolean.TRUE.equals(
            redisTemplate.opsForValue().setIfAbsent(redisKey, "1", PROCESSING_LOCK_TTL).block()
        );
    }
    
    /**
     * 释放用户处理锁
     */
    private void releaseLock(String guildId, String userId) {
        String redisKey = PROCESSING_KEY_PREFIX + guildId + ":" + userId;
        redisTemplate.delete(redisKey).subscribe();
    }

    /**
     * 流式对话 - 返回增量内容的 Flux
     * @param onComplete 完成时的回调，用于保存完整响应
     */
    public Flux<String> chatStream(String guildId, String userId, String channelId, String userMessage, 
                                    java.util.function.Consumer<String> onComplete) {
        // 尝试获取分布式锁
        if (!tryAcquireLock(guildId, userId)) {
            return Flux.error(new UserBusyException("请等待上一个问题回复完成"));
        }
        
        // 设置频道上下文，供工具使用
        channelHistoryTool.setContext(guildId, userId, channelId);
        
        return loadChatHistory(guildId, userId)
                .flatMapMany(history -> callAiStream(history, userMessage, guildId, userId, userMessage, onComplete))
                .doOnError(e -> log.error("AI 流式调用失败: {}", e.getMessage()))
                .doFinally(signal -> {
                    releaseLock(guildId, userId);  // 释放分布式锁
                    channelHistoryTool.clearContext(guildId, userId);  // 清理上下文
                })
                .onErrorResume(e -> {
                    if (e instanceof UserBusyException) {
                        return Flux.just(e.getMessage());
                    }
                    return Flux.just("AI 服务暂时不可用，请稍后再试");
                });
    }
    
    /**
     * 用户正在处理中的异常
     */
    public static class UserBusyException extends RuntimeException {
        public UserBusyException(String message) {
            super(message);
        }
    }

    /**
     * 流式调用 AI
     */
    private Flux<String> callAiStream(List<ChatMessage> history, String userMessage,
                                       String guildId, String userId, String originalMessage,
                                       java.util.function.Consumer<String> onComplete) {
        List<Message> messages = buildMessages(history, userMessage, guildId, userId);
        log.info("开始流式调用 AI，消息数: {}", messages.size());
        Prompt prompt = new Prompt(messages);
        StringBuilder fullContent = new StringBuilder();
        
        return chatClient.prompt(prompt)
                .stream()
                .content()
                .filter(chunk -> chunk != null && !chunk.isEmpty())
                .doOnNext(chunk -> fullContent.append(chunk))
                .doOnComplete(() -> {
                    String content = fullContent.toString();
                    log.info("AI 流式响应完成，内容长度: {}", content.length());
                    // 保存对话历史
                    saveChatHistory(guildId, userId, originalMessage, content);
                    if (onComplete != null) {
                        onComplete.accept(content);
                    }
                })
                .subscribeOn(Schedulers.boundedElastic());
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
     * 构建消息列表并调用 AI（带重试）
     */
    private Mono<String> callAi(List<ChatMessage> history, String userMessage) {
        List<Message> messages = buildMessages(history, userMessage);
        
        return Mono.fromCallable(() -> {
            log.info("开始调用 AI，消息数: {}, 可用工具数: {}", messages.size(), allTools.length);
            Prompt prompt = new Prompt(messages);
            var response = chatClient.prompt(prompt).call();
            String content = response.content();
            log.info("AI 响应完成，内容长度: {}", content != null ? content.length() : 0);
            return content;
        })
        .subscribeOn(Schedulers.boundedElastic())
        .retryWhen(Retry.backoff(3, java.time.Duration.ofSeconds(2))
                .maxBackoff(java.time.Duration.ofSeconds(10))
                .filter(this::isRetryableException)
                .doBeforeRetry(signal -> log.warn("AI 调用失败，第 {} 次重试: {}", 
                        signal.totalRetries() + 1, signal.failure().getMessage())));
    }
    
    /**
     * 判断是否可重试的异常（超时、网络错误等）
     */
    private boolean isRetryableException(Throwable e) {
        String message = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
        return message.contains("timeout") 
                || message.contains("connection") 
                || message.contains("reset")
                || message.contains("refused")
                || e instanceof java.net.SocketTimeoutException
                || e instanceof java.io.IOException;
    }

    /**
     * 构建 Spring AI 消息列表
     */
    private List<Message> buildMessages(List<ChatMessage> history, String userMessage) {
        return buildMessages(history, userMessage, null, null);
    }
    
    /**
     * 构建 Spring AI 消息列表（带上下文信息）
     */
    private List<Message> buildMessages(List<ChatMessage> history, String userMessage, String guildId, String userId) {
        List<Message> messages = new ArrayList<>();
        
        // 构建系统提示词，包含上下文信息
        String systemPrompt = getSystemPrompt();
        if (guildId != null && userId != null) {
            systemPrompt += "\n\n当前上下文信息（调用工具时使用）：\n- guildId: " + guildId + "\n- userId: " + userId;
        }
        messages.add(new SystemMessage(systemPrompt));

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
