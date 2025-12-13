package run.runnable.kage.command;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

/**
 * 统一命令接口 - 同时支持传统命令和 Slash 命令
 * 业务逻辑只需实现一次
 */
public interface UnifiedCommand extends Command, SlashCommand {

    /**
     * 统一的命令执行入口
     */
    void execute(CommandContext ctx);

    /**
     * 构建 Slash 命令数据，默认实现为无参数命令
     * 需要参数的命令应覆盖此方法
     */
    @Override
    default CommandData buildCommandData() {
        return Commands.slash(getName(), getDescription());
    }

    /**
     * 传统命令执行 - 委托给统一入口
     */
    @Override
    default void execute(MessageReceivedEvent event, String[] args) {
        MessageCommandContext ctx = new MessageCommandContext(event, args);
        execute(ctx);
    }

    /**
     * Slash 命令执行 - 委托给统一入口
     */
    @Override
    default void execute(SlashCommandInteractionEvent event) {
        SlashCommandContext ctx = new SlashCommandContext(event);
        execute(ctx);
    }
}
