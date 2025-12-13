package run.runnable.kage.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import run.runnable.kage.command.CommandRegistry;
import run.runnable.kage.domain.ChatMessage;
import run.runnable.kage.dto.deepseek.DeepSeekRequest;
import run.runnable.kage.dto.deepseek.DeepSeekResponse;
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
            你是布布，一个活泼可爱的 Discord 服务器忍者女管家。
            
            性格特点：
            - 热情友好，喜欢用可爱的语气说话
            - 乐于助人，耐心解答问题
            - 偶尔会卖萌，适当使用表情符号
            - 回答简洁有趣，不啰嗦
            
            重要：你可以记住对话历史！上面的消息就是你和用户之前的对话记录，请根据这些上下文来回答问题。
            
            你的能力：
            用户可以通过 @布布 或斜杠命令 / 来使用以下功能：
            %s
            
            注意事项：
            - 用中文回复，除非用户用其他语言提问
            - 不要在每句话都加表情，适度就好
            - 遇到不懂的问题诚实说不知道
            - 保持积极正面的态度
            - 当用户询问你能做什么或有哪些命令时，介绍上面列出的功能
            - 直接 @布布 说话也可以和你聊天，不需要特定命令
            """;

    private final WebClient webClient;
    private final ChatMessageRepository chatMessageRepository;
    private final CommandRegistry commandRegistry;

    public DeepSeekService(@Value("${deepseek.apiKeys}") String apiKey,
                           ChatMessageRepository chatMessageRepository,
                           @Lazy CommandRegistry commandRegistry) {
        this.chatMessageRepository = chatMessageRepository;
        this.commandRegistry = commandRegistry;
        this.webClient = WebClient.builder()
                .baseUrl("https://api.deepseek.com")
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    private String getSystemPrompt() {
        return String.format(SYSTEM_PROMPT_TEMPLATE, commandRegistry.getCommandListText());
    }

    public Mono<String> chat(String guildId, String userId, String userMessage) {
        // 先查历史，再请求 AI，最后异步保存
        return chatMessageRepository.findRecentByGuildAndUser(guildId, userId, MAX_HISTORY_SIZE)
                .collectList()
                .flatMap(history -> {
                    // 反转列表（数据库查询是 DESC，需要变成时间升序）
                    Collections.reverse(history);

                    // 构建请求消息
                    List<DeepSeekRequest.Message> messages = new ArrayList<>();
                    messages.add(DeepSeekRequest.Message.builder()
                            .role("system")
                            .content(getSystemPrompt())
                            .build());

                    // 添加历史消息
                    history.forEach(msg -> messages.add(DeepSeekRequest.Message.builder()
                            .role(msg.getRole())
                            .content(msg.getContent())
                            .build()));

                    // 添加当前用户消息
                    messages.add(DeepSeekRequest.Message.builder()
                            .role("user")
                            .content(userMessage)
                            .build());

                    DeepSeekRequest request = DeepSeekRequest.builder()
                            .model("deepseek-chat")
                            .stream(false)
                            .messages(messages)
                            .build();

                    return webClient.post()
                            .uri("/chat/completions")
                            .bodyValue(request)
                            .retrieve()
                            .bodyToMono(DeepSeekResponse.class);
                })
                .flatMap(response -> {
                    if (response.getChoices() != null && !response.getChoices().isEmpty()) {
                        String content = response.getChoices().get(0).getMessage().getContent();

                        // 异步保存用户消息和 AI 回复，不阻塞返回
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
                                .content(content)
                                .deleted(false)
                                .createdAt(now.plusNanos(1000)) // 确保顺序
                                .build();

                        // 异步保存，不等待结果
                        chatMessageRepository.save(userMsg)
                                .then(chatMessageRepository.save(assistantMsg))
                                .subscribe(
                                        v -> {},
                                        e -> log.error("保存对话历史失败: {}", e.getMessage())
                                );

                        return Mono.just(content);
                    }
                    return Mono.just("No response from AI");
                })
                .doOnError(e -> log.error("DeepSeek API error: {}", e.getMessage()))
                .onErrorReturn("AI 服务暂时不可用，请稍后再试");
    }

    /**
     * 清除用户在指定服务器的对话历史（软删除）
     */
    public Mono<Void> clearHistory(String guildId, String userId) {
        return chatMessageRepository.softDeleteByGuildAndUser(guildId, userId);
    }
}
