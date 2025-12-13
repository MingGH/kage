package run.runnable.kage.command.impl;

import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.springframework.stereotype.Component;
import run.runnable.kage.command.Command;
import run.runnable.kage.service.DeepSeekService;

@Component
@RequiredArgsConstructor
public class ClearCommand implements Command {

    private final DeepSeekService deepSeekService;

    @Override
    public String getName() {
        return "clear";
    }

    @Override
    public String getDescription() {
        return "清除 AI 对话历史，开始新对话";
    }

    @Override
    public void execute(MessageReceivedEvent event, String[] args) {
        if (!event.isFromGuild()) {
            event.getChannel().sendMessage("❌ 该命令只能在服务器中使用").queue();
            return;
        }

        String guildId = event.getGuild().getId();
        String userId = event.getAuthor().getId();
        deepSeekService.clearHistory(guildId, userId)
                .subscribe(
                        v -> event.getChannel().sendMessage("✨ 对话历史已清除，可以开始新话题了").queue(),
                        e -> event.getChannel().sendMessage("❌ 清除失败: " + e.getMessage()).queue()
                );
    }
}
