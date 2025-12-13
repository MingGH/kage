package run.runnable.kage.command;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 命令注册表 - 存储所有命令的元信息
 * 用于打破 CommandManager <-> DeepSeekService 的循环依赖
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CommandRegistry {

    private final List<Command> commands;

    @Getter
    private final Map<String, Command> commandMap = new HashMap<>();

    @Getter
    private String commandListText;

    @PostConstruct
    public void init() {
        StringBuilder sb = new StringBuilder();
        for (Command command : commands) {
            commandMap.put(command.getName().toLowerCase(), command);
            sb.append("- ").append(command.getName()).append(": ").append(command.getDescription()).append("\n");
            log.info("注册命令: {}", command.getName());
        }
        this.commandListText = sb.toString();
    }

    public Command getCommand(String name) {
        return commandMap.get(name.toLowerCase());
    }

    public int getCommandCount() {
        return commands.size();
    }
}
