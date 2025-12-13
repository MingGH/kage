package run.runnable.kage.command.impl;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.springframework.stereotype.Component;
import run.runnable.kage.command.Command;

@Component
public class HelloCommand implements Command {

    @Override
    public String getName() {
        return "hello";
    }

    @Override
    public String getDescription() {
        return "打招呼";
    }

    @Override
    public void execute(MessageReceivedEvent event, String[] args) {
        String userName = event.getAuthor().getName();
        event.getChannel().sendMessage("Hello " + userName + "! 👋").queue();
    }
}
