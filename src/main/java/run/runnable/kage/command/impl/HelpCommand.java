package run.runnable.kage.command.impl;

import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.springframework.stereotype.Component;
import run.runnable.kage.command.Command;

import java.util.List;

@Component
@RequiredArgsConstructor
public class HelpCommand implements Command {

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
    public void execute(MessageReceivedEvent event, String[] args) {
        StringBuilder sb = new StringBuilder("**Kage Bot 命令列表:**\n");

        commands.forEach(cmd ->
                sb.append("`!").append(cmd.getName()).append("` - ")
                  .append(cmd.getDescription()).append("\n")
        );

        event.getChannel().sendMessage(sb.toString()).queue();
    }
}
