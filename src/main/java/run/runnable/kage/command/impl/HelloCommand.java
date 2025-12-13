package run.runnable.kage.command.impl;

import org.springframework.stereotype.Component;
import run.runnable.kage.command.CommandContext;
import run.runnable.kage.command.UnifiedCommand;

@Component
public class HelloCommand implements UnifiedCommand {

    @Override
    public String getName() {
        return "hello";
    }

    @Override
    public String getDescription() {
        return "打招呼";
    }

    @Override
    public void execute(CommandContext ctx) {
        String userName = ctx.getUser().getName();
        ctx.reply("Hello " + userName + "! 👋");
    }
}
