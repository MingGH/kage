package run.runnable.kage.command.impl;

import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import org.springframework.stereotype.Component;
import run.runnable.kage.command.CommandContext;
import run.runnable.kage.command.MessageCommandContext;
import run.runnable.kage.command.UnifiedCommand;
import run.runnable.kage.service.MusicService;

/**
 * 调节音量
 */
@Component
@RequiredArgsConstructor
public class VolumeCommand implements UnifiedCommand {

    private final MusicService musicService;

    @Override
    public String getName() {
        return "volume";
    }

    @Override
    public String getDescription() {
        return "调节音量 (0-100)";
    }

    @Override
    public CommandData buildCommandData() {
        return Commands.slash(getName(), getDescription())
                .addOption(OptionType.INTEGER, "level", "音量大小 (0-100)", true);
    }

    @Override
    public void execute(MessageReceivedEvent event, String[] args) {
        MessageCommandContext ctx = new MessageCommandContext(event, args);
        if (args.length > 0) {
            ctx.setArg("level", args[0]);
        }
        execute(ctx);
    }

    @Override
    public void execute(CommandContext ctx) {
        if (!ctx.isFromGuild()) {
            ctx.replyEphemeral("❌ 该命令只能在服务器中使用");
            return;
        }

        Integer level = ctx.getInteger("level");
        if (level == null) {
            ctx.reply("用法: `/volume <0-100>`");
            return;
        }

        if (level < 0 || level > 100) {
            ctx.reply("❌ 音量范围是 0-100");
            return;
        }

        musicService.setVolume(ctx.getGuild(), level);
        ctx.reply("🔊 音量已设置为 " + level + "%");
    }
}
