package run.runnable.kage.listener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.springframework.stereotype.Component;
import run.runnable.kage.command.CommandManager;
import run.runnable.kage.domain.UserMessage;
import run.runnable.kage.service.LeaderboardStatsService;
import run.runnable.kage.service.MessageQueueService;
import run.runnable.kage.service.MessageRateLimitService;

import java.time.Duration;
import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class DiscordMessageListener extends ListenerAdapter {

    private final CommandManager commandManager;
    private final MessageQueueService messageQueueService;
    private final LeaderboardStatsService leaderboardStatsService;
    private final MessageRateLimitService messageRateLimitService;

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        // 忽略机器人自己的消息
        if (event.getAuthor().isBot()) {
            return;
        }

        String message = event.getMessage().getContentRaw();
        String channelName = event.getChannel().getName();
        String userName = event.getAuthor().getName();

        log.info("收到消息 - 频道: {}, 用户: {}, 内容: {}", channelName, userName, message);

        // 记录用户消息（仅服务器内的消息），异常不影响命令处理
        if (event.isFromGuild()) {
            String guildId = event.getGuild().getId();
            String userId = event.getAuthor().getId();
            
            // 检查消息频率限制
            try {
                MessageRateLimitService.RateLimitResult result = 
                        messageRateLimitService.recordAndCheck(guildId, userId).block();
                
                if (result != null && !result.isAllowed()) {
                    handleRateLimitViolation(event, result);
                    return; // 被限制时不处理消息
                }
            } catch (Exception e) {
                log.error("频率限制检查异常: {}", e.getMessage());
                // 异常时允许消息通过，避免影响正常使用
            }
            
            try {
                pushUserMessage(event);
                // 更新摸鱼排行榜统计
                recordLeaderboardStats(event);
            } catch (Exception e) {
                log.error("记录消息异常: {}", e.getMessage());
            }
        }

        // 交给命令管理器处理
        commandManager.handleMessage(event);
    }
    
    /**
     * 处理频率限制违规
     */
    private void handleRateLimitViolation(MessageReceivedEvent event, MessageRateLimitService.RateLimitResult result) {
        String userName = event.getAuthor().getName();
        
        if (result.isTriggered()) {
            // 新触发的禁言 - 使用 Discord Timeout 功能
            int muteMinutes = result.getMuteMinutes();
            log.warn("用户 {} 触发消息频率限制，禁言 {} 分钟", userName, muteMinutes);
            
            // 应用 Discord Timeout
            event.getMember().timeoutFor(Duration.ofMinutes(muteMinutes))
                    .reason("消息发送过于频繁（1分钟内超过60条）")
                    .queue(
                            success -> log.info("已对用户 {} 应用 {} 分钟禁言", userName, muteMinutes),
                            error -> log.error("应用禁言失败: {}", error.getMessage())
                    );
            
            // 发送提示消息
            event.getChannel().sendMessage(
                    String.format("⚠️ **%s** 消息发送过于频繁（1分钟内超过60条），已被禁言 **%d 分钟**。\n" +
                            "💡 提示：多次触发将会延长禁言时间。", userName, muteMinutes)
            ).queue(
                    success -> {},
                    error -> log.error("发送禁言提示失败: {}", error.getMessage())
            );
            
            // 删除触发限制的消息
            event.getMessage().delete().queue(
                    success -> {},
                    error -> log.debug("删除消息失败: {}", error.getMessage())
            );
        } else {
            // 已在禁言中（理论上 Discord Timeout 后用户无法发消息，这里作为备用）
            log.debug("用户 {} 处于禁言状态，剩余 {} 秒", userName, result.getMuteSeconds());
            // 删除禁言期间的消息（如果 timeout 失效的情况）
            event.getMessage().delete().queue(
                    success -> {},
                    error -> log.debug("删除消息失败: {}", error.getMessage())
            );
        }
    }

    private void pushUserMessage(MessageReceivedEvent event) {
        UserMessage userMessage = UserMessage.builder()
                .guildId(event.getGuild().getId())
                .channelId(event.getChannel().getId())
                .userId(event.getAuthor().getId())
                .userName(event.getAuthor().getName())
                .content(event.getMessage().getContentRaw())
                .messageId(event.getMessageId())
                .createdAt(LocalDateTime.now())
                .build();

        messageQueueService.pushMessage(userMessage);
    }

    /**
     * 记录消息到摸鱼排行榜统计
     */
    private void recordLeaderboardStats(MessageReceivedEvent event) {
        String guildId = event.getGuild().getId();
        String userId = event.getAuthor().getId();
        String userName = event.getAuthor().getName();
        String content = event.getMessage().getContentRaw();

        leaderboardStatsService.recordMessage(guildId, userId, userName, content)
                .subscribe(
                        v -> {},
                        e -> log.error("更新摸鱼排行榜统计失败: guildId={}, userId={}", guildId, userId, e)
                );
    }
}
