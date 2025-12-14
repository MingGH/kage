package run.runnable.kage.command.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import run.runnable.kage.command.CommandContext;
import run.runnable.kage.command.UnifiedCommand;
import run.runnable.kage.service.CountdownService;

/**
 * 取消下班倒计时命令
 */
@Component
@RequiredArgsConstructor
public class CountdownCancelCommand implements UnifiedCommand {

    private final CountdownService countdownService;

    @Override
    public String getName() {
        return "countdown-cancel";
    }

    @Override
    public String getDescription() {
        return "取消下班倒计时";
    }

    @Override
    public void execute(CommandContext ctx) {
        countdownService.cancelCountdown(
                ctx.getGuild().getId(),
                ctx.getUser().getId()
        ).subscribe(ctx::reply);
    }
}
