package run.runnable.kage.command.impl;

import org.springframework.stereotype.Component;
import run.runnable.kage.command.CommandContext;
import run.runnable.kage.command.UnifiedCommand;

@Component
public class PingCommand implements UnifiedCommand {

    @Override
    public String getName() {
        return "ping";
    }

    @Override
    public String getDescription() {
        return "测试机器人响应";
    }

    @Override
    public void execute(CommandContext ctx) {
        ctx.reply("Pong! 🏓");
    }
}
