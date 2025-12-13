package run.runnable.kage.command;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

/**
 * 命令接口 - 所有命令都需要实现此接口
 */
public interface Command {

    /**
     * 命令名称（不含前缀，如 "ping"）
     */
    String getName();

    /**
     * 命令描述（用于 help 显示）
     */
    String getDescription();

    /**
     * 执行命令
     */
    void execute(MessageReceivedEvent event, String[] args);
}
