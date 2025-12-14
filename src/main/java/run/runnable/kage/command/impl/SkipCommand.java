package run.runnable.kage.command.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import run.runnable.kage.command.CommandContext;
import run.runnable.kage.command.UnifiedCommand;
import run.runnable.kage.service.MusicService;

/**
 * 跳过当前歌曲
 */
@Component
@RequiredArgsConstructor
public class SkipCommand implements UnifiedCommand {

    private final MusicService musicService;

    @Override
    public String getName() {
        return "skip";
    }

    @Override
    public String getDescription() {
        return "跳过当前歌曲";
    }

    @Override
    public void execute(CommandContext ctx) {
        if (!ctx.isFromGuild()) {
            ctx.replyEphemeral("❌ 该命令只能在服务器中使用");
            return;
        }

        musicService.skip(ctx.getGuild());
        ctx.reply("⏭️ 已跳过当前歌曲");
    }
}
