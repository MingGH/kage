package run.runnable.kage.command.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import run.runnable.kage.command.CommandContext;
import run.runnable.kage.command.UnifiedCommand;
import run.runnable.kage.service.MusicService;

/**
 * 停止音乐并离开语音频道
 */
@Component
@RequiredArgsConstructor
public class StopMusicCommand implements UnifiedCommand {

    private final MusicService musicService;

    @Override
    public String getName() {
        return "stop";
    }

    @Override
    public String getDescription() {
        return "停止播放音乐并离开语音频道";
    }

    @Override
    public void execute(CommandContext ctx) {
        if (!ctx.isFromGuild()) {
            ctx.replyEphemeral("❌ 该命令只能在服务器中使用");
            return;
        }

        musicService.stop(ctx.getGuild());
        musicService.leaveVoiceChannel(ctx.getGuild());
        ctx.reply("⏹️ 已停止播放并离开语音频道");
    }
}
