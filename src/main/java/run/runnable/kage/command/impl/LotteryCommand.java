package run.runnable.kage.command.impl;

import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import org.springframework.stereotype.Component;
import run.runnable.kage.command.CommandContext;
import run.runnable.kage.command.UnifiedCommand;
import run.runnable.kage.service.LotteryService;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class LotteryCommand implements UnifiedCommand {

    private final LotteryService lotteryService;

    private static final Pattern PATTERN = Pattern.compile("(.+?)\\s+(\\d+)\\s+(\\d+)");

    @Override
    public String getName() {
        return "抽奖";
    }

    @Override
    public String getDescription() {
        return "发起抽奖 (用法: 抽奖 奖品 中奖人数 持续分钟)";
    }

    @Override
    public CommandData buildCommandData() {
        return Commands.slash("lottery", "发起抽奖活动")
                .addOption(OptionType.STRING, "prize", "奖品名称", true)
                .addOption(OptionType.INTEGER, "winners", "中奖人数", true)
                .addOption(OptionType.INTEGER, "minutes", "持续时间（分钟）", true);
    }

    @Override
    public void execute(CommandContext ctx) {
        if (!ctx.isFromGuild()) {
            ctx.replyEphemeral("❌ 该命令只能在服务器中使用");
            return;
        }

        // 解析参数
        String prize;
        int winnerCount;
        int durationMinutes;

        // 尝试从 Slash 命令参数获取
        String slashPrize = ctx.getString("prize");
        Integer slashWinners = ctx.getInteger("winners");
        Integer slashMinutes = ctx.getInteger("minutes");

        if (slashPrize != null && slashWinners != null && slashMinutes != null) {
            prize = slashPrize;
            winnerCount = slashWinners;
            durationMinutes = slashMinutes;
        } else {
            // 从传统命令参数解析
            String rawArgs = ctx.getRawArgs();
            if (rawArgs == null || rawArgs.isBlank()) {
                ctx.reply("用法: `!抽奖 奖品名称 中奖人数 持续分钟`\n例如: `!抽奖 Steam游戏 1 30`");
                return;
            }

            Matcher matcher = PATTERN.matcher(rawArgs);
            if (!matcher.matches()) {
                ctx.reply("格式错误！用法: `!抽奖 奖品名称 中奖人数 持续分钟`");
                return;
            }

            prize = matcher.group(1).trim();
            winnerCount = Integer.parseInt(matcher.group(2));
            durationMinutes = Integer.parseInt(matcher.group(3));
        }

        if (winnerCount < 1 || winnerCount > 100) {
            ctx.replyEphemeral("❌ 中奖人数需要在 1-100 之间");
            return;
        }

        if (durationMinutes < 1 || durationMinutes > 10080) {
            ctx.replyEphemeral("❌ 持续时间需要在 1-10080 分钟之间");
            return;
        }

        LocalDateTime endTime = LocalDateTime.now().plusMinutes(durationMinutes);
        String guildId = ctx.getGuild().getId();
        String channelId = ctx.getChannel().getId();
        String creatorId = ctx.getUser().getId();

        ctx.deferReply(hook -> {
            lotteryService.createLottery(guildId, channelId, creatorId, prize, winnerCount, endTime)
                    .subscribe(lottery -> {
                        String endTimeStr = endTime.format(DateTimeFormatter.ofPattern("MM-dd HH:mm"));

                        String message = String.format("""
                                🎁 **抽奖活动**
                                
                                奖品: %s
                                中奖人数: %d
                                开奖时间: %s
                                发起人: <@%s>
                                
                                点击下方按钮参与抽奖！
                                """, prize, winnerCount, endTimeStr, creatorId);

                        // 由于 hook 只能发送文本，需要在频道中发送带按钮的消息
                        ctx.getChannel().sendMessage(message)
                                .setActionRow(
                                        Button.primary("lottery_join_" + lottery.getId(), "参与抽奖")
                                                .withEmoji(Emoji.fromUnicode("🎉"))
                                )
                                .queue(msg -> {
                                    lotteryService.updateMessageId(lottery.getId(), msg.getId()).subscribe();
                                    hook.sendMessage("✅ 抽奖已创建！");
                                });
                    });
        });
    }
}
