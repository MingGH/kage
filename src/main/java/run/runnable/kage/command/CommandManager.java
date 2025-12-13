package run.runnable.kage.command;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 命令管理器 - 负责注册和分发 @机器人 命令
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CommandManager {

    private static final String EVENT_KEY_PREFIX = "discord:event:";
    private static final Duration EVENT_EXPIRE = Duration.ofMinutes(5);
    private static final String DEFAULT_HINT = "你好呀～我是布布！\n\n" +
            "你可以用以下方式和我互动：\n" +
            "• `@布布 ask 问题` - 向我提问\n" +
            "• `@布布 help` - 查看所有命令\n" +
            "• 或者使用斜杠命令 `/help`";

    private final List<Command> commands;
    private final ReactiveStringRedisTemplate redisTemplate;
    private final Map<String, Command> commandMap = new HashMap<>();

    @PostConstruct
    public void init() {
        for (Command command : commands) {
            commandMap.put(command.getName().toLowerCase(), command);
            log.info("注册命令: {}", command.getName());
        }
    }

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
        // 如果 @机器人 后面没有内容，显示默认提示
        if (commandContent.isBlank()) {
            event.getChannel().sendMessage(DEFAULT_HINT).queue();
            return;
        }

        String[] parts = commandContent.split("\\s+");
        String commandName = parts[0].toLowerCase();
        Command cmd = commandMap.get(commandName);

        if (cmd == null) {
            event.getChannel().sendMessage("布布不明白你的意思呢～\n输入 `@布布 help` 查看可用命令").queue();
            return;
        }

        String[] args = new String[parts.length - 1];
        System.arraycopy(parts, 1, args, 0, args.length);
        log.info("执行命令: {} by {}", commandName, event.getAuthor().getName());
        cmd.execute(event, args);
    }

    public Map<String, Command> getCommands() {
        return commandMap;
    }
}
