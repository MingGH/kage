package run.runnable.kage.command;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import run.runnable.kage.service.DeepSeekService;

import java.time.Duration;
import java.util.Map;

/**
 * 命令管理器 - 负责分发 @机器人 命令
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CommandManager {

    private static final String EVENT_KEY_PREFIX = "discord:event:";
    private static final Duration EVENT_EXPIRE = Duration.ofMinutes(5);

    private final CommandRegistry commandRegistry;
    private final ReactiveStringRedisTemplate redisTemplate;
    private final DeepSeekService deepSeekService;

    /**
     * 处理 @机器人 消息
     * 使用 Redis 防止重复处理（本地开发和线上同时运行时）
     */
    public void handleMessage(MessageReceivedEvent event) {
        // 只处理 @机器人 的消息
        if (!event.getMessage().getMentions().isMentioned(event.getJDA().getSelfUser())) {
            return;
        }

        String content = event.getMessage().getContentRaw().trim();
        // 移除 @机器人 部分
        String commandContent = content.replaceFirst("<@!?" + event.getJDA().getSelfUser().getId() + ">\\s*", "").trim();

        // 用消息 ID 作为去重 key
        String eventKey = EVENT_KEY_PREFIX + event.getMessageId();

        redisTemplate.opsForValue()
                .setIfAbsent(eventKey, "1", EVENT_EXPIRE)
                .subscribe(success -> {
                    if (Boolean.TRUE.equals(success)) {
                        executeCommand(event, commandContent);
                    } else {
                        log.debug("事件已被其他实例处理: {}", event.getMessageId());
                    }
                });
    }

    private void executeCommand(MessageReceivedEvent event, String commandContent) {
        // 如果 @机器人 后面没有内容，当作打招呼
        if (commandContent.isBlank()) {
            chatWithAI(event, "你好");
            return;
        }

        String[] parts = commandContent.split("\\s+");
        String commandName = parts[0].toLowerCase();
        Command cmd = commandRegistry.getCommand(commandName);

        // 如果没有匹配到命令，直接当作 AI 对话
        if (cmd == null) {
            chatWithAI(event, commandContent);
            return;
        }

        String[] args = new String[parts.length - 1];
        System.arraycopy(parts, 1, args, 0, args.length);
        log.info("执行命令: {} by {}", commandName, event.getAuthor().getName());
        cmd.execute(event, args);
    }

    /**
     * 调用 DeepSeek AI 进行对话
     */
    private void chatWithAI(MessageReceivedEvent event, String message) {
        if (!event.isFromGuild()) {
            event.getMessage().reply("❌ 该功能只能在服务器中使用").queue();
            return;
        }

        String guildId = event.getGuild().getId();
        String userId = event.getAuthor().getId();

        event.getChannel().sendMessage("🤔 思考中...").queue(thinkingMsg -> {
            deepSeekService.chat(guildId, userId, message)
                    .subscribe(
                            answer -> {
                                thinkingMsg.delete().queue();
                                String reply = answer.length() > 1900
                                        ? answer.substring(0, 1900) + "..."
                                        : answer;
                                // 使用 reply 回复原消息
                                event.getMessage().reply(reply).queue();
                            },
                            error -> {
                                thinkingMsg.delete().queue();
                                event.getMessage().reply("❌ 出错了: " + error.getMessage()).queue();
                            }
                    );
        });
    }

    public Map<String, Command> getCommands() {
        return commandRegistry.getCommandMap();
    }
}
