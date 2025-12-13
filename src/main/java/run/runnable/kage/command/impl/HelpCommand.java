package run.runnable.kage.command.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import run.runnable.kage.command.Command;
import run.runnable.kage.command.CommandContext;
import run.runnable.kage.command.UnifiedCommand;

import java.util.List;

@Component
@RequiredArgsConstructor
public class HelpCommand implements UnifiedCommand {

    private final List<Command> commands;

    @Override
    public String getName() {
        return "help";
    }

    @Override
    public String getDescription() {
        return "显示帮助信息";
    }

    @Override
    public void execute(CommandContext ctx) {
        StringBuilder sb = new StringBuilder("**布布命令列表:**\n\n");
        sb.append("**Slash 命令：**\n");

        commands.forEach(cmd ->
                sb.append("`/").append(cmd.getName()).append("` - ")
                  .append(cmd.getDescription()).append("\n")
        );

        sb.append("\n**传统命令（@布布 或 !）：**\n");
        commands.forEach(cmd ->
                sb.append("`!").append(cmd.getName()).append("` - ")
                  .append(cmd.getDescription()).append("\n")
        );

        ctx.reply(sb.toString());
    }
}
