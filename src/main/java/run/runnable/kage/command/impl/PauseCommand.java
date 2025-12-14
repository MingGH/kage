package run.runnable.kage.command.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import run.runnable.kage.command.CommandContext;
import run.runnable.kage.command.UnifiedCommand;
import run.runnable.kage.service.MusicService;

/**
 * 暂停/恢复播放
 */
@Component
@RequiredArgsConstructor
public class PauseCommand implements UnifiedCommand {

    private final MusicService musicService;

    @Override
    public String getName() {
        return "pause";
    }

    @Override
    public String getDescription() {
        return "暂停/恢复播放";
    }

    @Override
    public void execute(CommandContext ctx) {
        if (!ctx.isFromGuild()) {
            ctx.replyEphemeral("❌ 该命令只能在服务器中使用");
            return;
        }

        boolean paused = musicService.togglePause(ctx.getGuild());
        ctx.reply(paused ? "⏸️ 已暂停播放" : "▶️ 已恢复播放");
    }
}
