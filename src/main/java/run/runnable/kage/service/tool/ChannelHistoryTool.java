package run.runnable.kage.service.tool;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import run.runnable.kage.domain.UserMessage;
import run.runnable.kage.repository.UserMessageRepository;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AI 工具：查询频道聊天记录
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChannelHistoryTool {

    private final UserMessageRepository userMessageRepository;
    
    // 使用 ConcurrentHashMap 存储上下文，key 为 guildId:userId
    private final Map<String, ChannelContext> contextMap = new ConcurrentHashMap<>();
    
    public void setContext(String guildId, String userId, String channelId) {
        contextMap.put(guildId + ":" + userId, new ChannelContext(guildId, channelId));
    }
    
    public void clearContext(String guildId, String userId) {
        contextMap.remove(guildId + ":" + userId);
    }
    
    public ChannelContext getContext(String guildId, String userId) {
        return contextMap.get(guildId + ":" + userId);
    }
    
    public record ChannelContext(String guildId, String channelId) {}

    @Tool(description = "查询当前频道最近的聊天记录。当用户询问'刚才聊了什么'、'总结一下讨论'、'大家在说什么'等问题时使用此工具。")
    public String getRecentChannelMessages(
            @ToolParam(description = "服务器ID") String guildId,
            @ToolParam(description = "用户ID") String userId,
            @ToolParam(description = "查询最近多少分钟的消息，默认10分钟，最大30分钟") Integer minutes
    ) {
        ChannelContext ctx = getContext(guildId, userId);
        if (ctx == null) {
            log.warn("未找到频道上下文: guildId={}, userId={}", guildId, userId);
            return "无法获取频道信息，请重试";
        }
        
        int mins = (minutes == null || minutes <= 0) ? 10 : Math.min(minutes, 30);
        LocalDateTime since = LocalDateTime.now().minusMinutes(mins);
        
        log.info("查询频道聊天记录: guildId={}, channelId={}, 最近{}分钟", 
                ctx.guildId(), ctx.channelId(), mins);
        
        List<UserMessage> messages = userMessageRepository
                .findRecentByChannel(ctx.guildId(), ctx.channelId(), since, 50)
                .collectList()
                .block();
        
        if (messages == null || messages.isEmpty()) {
            return "最近" + mins + "分钟内没有聊天记录";
        }
        
        // 反转顺序，让最早的消息在前面
        Collections.reverse(messages);
        
        StringBuilder sb = new StringBuilder();
        sb.append("最近").append(mins).append("分钟的聊天记录（共").append(messages.size()).append("条）：\n\n");
        
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss");
        for (UserMessage msg : messages) {
            sb.append("[").append(msg.getCreatedAt().format(formatter)).append("] ");
            sb.append(msg.getUserName()).append(": ");
            // 截断过长的消息
            String content = msg.getContent();
            if (content.length() > 200) {
                content = content.substring(0, 200) + "...";
            }
            sb.append(content).append("\n");
        }
        
        return sb.toString();
    }
}
