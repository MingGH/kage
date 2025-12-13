package run.runnable.kage.listener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.springframework.stereotype.Component;
import run.runnable.kage.command.CommandManager;
import run.runnable.kage.domain.UserMessage;
import run.runnable.kage.service.MessageQueueService;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class DiscordMessageListener extends ListenerAdapter {

    private final CommandManager commandManager;
    private final MessageQueueService messageQueueService;

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
            try {
                pushUserMessage(event);
            } catch (Exception e) {
                log.error("记录消息异常: {}", e.getMessage());
            }
        }

        // 交给命令管理器处理
        commandManager.handleMessage(event);
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
}
