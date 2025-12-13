package run.runnable.kage.command;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;

/**
 * Slash 命令接口
 */
public interface SlashCommand {

    /**
     * 命令名称
     */
    String getName();

    /**
     * 命令描述
     */
    String getDescription();

    /**
     * 构建命令数据（用于注册到 Discord）
     */
    CommandData buildCommandData();

    /**
     * 执行命令
     */
    void execute(SlashCommandInteractionEvent event);
}