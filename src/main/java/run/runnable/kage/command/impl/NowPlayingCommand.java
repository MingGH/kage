package run.runnable.kage.command.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import run.runnable.kage.command.CommandContext;
import run.runnable.kage.command.UnifiedCommand;
import run.runnable.kage.service.MusicService;

/**
 * 显示当前播放的歌曲
 */
@Component
@RequiredArgsConstructor
public class NowPlayingCommand implements UnifiedCommand {

    private final MusicService musicService;

    @Override
    public String getName() {
        return "np";
    }

    @Override
    public String getDescription() {
        return "显示当前播放的歌曲";
    }

    @Override
    public void execute(CommandContext ctx) {
        if (!ctx.isFromGuild()) {
            ctx.replyEphemeral("❌ 该命令只能在服务器中使用");
            return;
        }

        ctx.reply(musicService.getNowPlaying(ctx.getGuild()));
    }
}
