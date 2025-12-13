package run.runnable.kage.command.impl;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.springframework.stereotype.Component;
import run.runnable.kage.command.Command;

@Component
public class PingCommand implements Command {

    @Override
    public String getName() {
        return "ping";
    }

    @Override
    public String getDescription() {
        return "测试机器人响应";
    }

    @Override
    public void execute(MessageReceivedEvent event, String[] args) {
        event.getChannel().sendMessage("Pong! 🏓").queue();
    }
}
