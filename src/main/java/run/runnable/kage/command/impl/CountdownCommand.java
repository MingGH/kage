package run.runnable.kage.command.impl;

import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import org.springframework.stereotype.Component;
import run.runnable.kage.command.CommandContext;
import run.runnable.kage.command.UnifiedCommand;
import run.runnable.kage.service.CountdownService;

/**
 * 下班倒计时命令
 */
@Component
@RequiredArgsConstructor
public class CountdownCommand implements UnifiedCommand {

    private final CountdownService countdownService;

    @Override
    public String getName() {
        return "countdown";
    }

    @Override
    public String getDescription() {
        return "设置下班倒计时（例如 /countdown 18:00）";
    }

    @Override
    public CommandData buildCommandData() {
        return Commands.slash(getName(), getDescription())
                .addOption(OptionType.STRING, "time", "下班时间（24小时制），例如 18:00 或 17:30", true);
    }

    @Override
    public void execute(CommandContext ctx) {
        String time = ctx.getString("time");
        
        if (time == null || time.isBlank()) {
            // 没有参数，查询当前状态
            countdownService.getCountdownStatus(
                    ctx.getGuild().getId(),
                    ctx.getUser().getId()
            ).subscribe(ctx::reply);
            return;
        }
        
        // 设置倒计时
        countdownService.setCountdown(
                ctx.getGuild().getId(),
                ctx.getChannel().getId(),
                ctx.getUser().getId(),
                time.trim()
        ).subscribe(ctx::reply);
    }
}
