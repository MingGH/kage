package run.runnable.kage.command.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import run.runnable.kage.command.CommandContext;
import run.runnable.kage.command.UnifiedCommand;
import run.runnable.kage.service.DeepSeekService;

@Component
@RequiredArgsConstructor
public class ClearCommand implements UnifiedCommand {

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
    public void execute(CommandContext ctx) {
        if (!ctx.isFromGuild()) {
            ctx.replyEphemeral("❌ 该命令只能在服务器中使用");
            return;
        }

        String guildId = ctx.getGuild().getId();
        String userId = ctx.getUser().getId();

        deepSeekService.clearHistory(guildId, userId)
                .subscribe(
                        v -> ctx.reply("✨ 对话历史已清除，可以开始新话题了"),
                        e -> ctx.reply("❌ 清除失败: " + e.getMessage())
                );
    }
}
