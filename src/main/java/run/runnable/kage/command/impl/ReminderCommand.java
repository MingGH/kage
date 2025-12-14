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
import run.runnable.kage.service.ReminderService;

/**
 * 定时提醒命令
 * 用法: /remind 30m 喝水
 */
@Component
@RequiredArgsConstructor
public class ReminderCommand implements UnifiedCommand {

    private final ReminderService reminderService;

    @Override
    public String getName() {
        return "remind";
    }

    @Override
    public String getDescription() {
        return "设置定时提醒";
    }

    @Override
    public CommandData buildCommandData() {
        return Commands.slash(getName(), getDescription())
                .addOption(OptionType.STRING, "time", "提醒时间 (如 30s, 5m, 2h, 1d)", true)
                .addOption(OptionType.STRING, "message", "提醒内容", true);
    }

    @Override
    public void execute(MessageReceivedEvent event, String[] args) {
        MessageCommandContext ctx = new MessageCommandContext(event, args);
        if (args.length >= 2) {
            ctx.setArg("time", args[0]);
            ctx.setArg("message", String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)));
        } else if (args.length == 1) {
            ctx.setArg("time", args[0]);
        }
        execute(ctx);
    }

    @Override
    public void execute(CommandContext ctx) {
        if (!ctx.isFromGuild()) {
            ctx.replyEphemeral("❌ 该命令只能在服务器中使用");
            return;
        }

        String time = ctx.getString("time");
        String message = ctx.getString("message");

        if (time == null || time.isBlank()) {
            ctx.reply("用法: `/remind <时间> <内容>`\n\n时间格式: 30s(秒), 5m(分钟), 2h(小时), 1d(天)\n例如: `/remind 30m 喝水`");
            return;
        }

        if (message == null || message.isBlank()) {
            ctx.reply("❌ 请输入提醒内容\n例如: `/remind 30m 喝水`");
            return;
        }

        String guildId = ctx.getGuild().getId();
        String channelId = ctx.getChannel().getId();
        String userId = ctx.getUser().getId();

        ctx.deferReply(hook -> {
            reminderService.setReminder(guildId, channelId, userId, time, message)
                    .subscribe(hook::sendMessage);
        });
    }
}
