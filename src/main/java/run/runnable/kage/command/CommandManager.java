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
import java.util.Optional;

/**
 * 命令管理器 - 负责注册和分发命令
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CommandManager {

    private static final String COMMAND_PREFIX = "!";
    private static final String EVENT_KEY_PREFIX = "discord:event:";
    private static final Duration EVENT_EXPIRE = Duration.ofMinutes(5);

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
     * 处理消息，如果是命令则执行
     * 使用 Redis 防止重复处理（本地开发和线上同时运行时）
     */
    public void handleMessage(MessageReceivedEvent event) {
        String content = event.getMessage().getContentRaw();

        if (!content.startsWith(COMMAND_PREFIX)) {
            return;
        }

        String[] parts = content.substring(COMMAND_PREFIX.length()).split("\\s+");
        if (parts.length == 0 || parts[0].isEmpty()) {
            return;
        }

        String commandName = parts[0].toLowerCase();
        Command cmd = commandMap.get(commandName);

        // 用消息 ID 作为去重 key
        String eventKey = EVENT_KEY_PREFIX + event.getMessageId();

        // setIfAbsent 返回 true 表示设置成功（第一个处理的实例）
        redisTemplate.opsForValue()
                .setIfAbsent(eventKey, "1", EVENT_EXPIRE)
                .subscribe(success -> {
                    if (Boolean.TRUE.equals(success)) {
                        if (cmd == null) {
                            event.getChannel().sendMessage("布布不明白你的意思呢～").queue();
                            return;
                        }
                        String[] args = new String[parts.length - 1];
                        System.arraycopy(parts, 1, args, 0, args.length);
                        log.info("执行命令: {} by {}", commandName, event.getAuthor().getName());
                        cmd.execute(event, args);
                    } else {
                        log.debug("事件已被其他实例处理: {}", event.getMessageId());
                    }
                });
    }

    public Map<String, Command> getCommands() {
        return commandMap;
    }
}
