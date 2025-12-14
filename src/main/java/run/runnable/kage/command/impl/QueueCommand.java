package run.runnable.kage.command.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import run.runnable.kage.command.CommandContext;
import run.runnable.kage.command.UnifiedCommand;
import run.runnable.kage.service.MusicService;

/**
 * 查看播放队列
 */
@Component
@RequiredArgsConstructor
public class QueueCommand implements UnifiedCommand {

    private final MusicService musicService;

    @Override
    public String getName() {
        return "queue";
    }

    @Override
    public String getDescription() {
        return "查看播放队列";
    }

    @Override
    public void execute(CommandContext ctx) {
        if (!ctx.isFromGuild()) {
            ctx.replyEphemeral("❌ 该命令只能在服务器中使用");
            return;
        }

        ctx.reply(musicService.getQueueList(ctx.getGuild()));
    }
}
