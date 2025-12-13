package run.runnable.kage.command.impl;

import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import org.springframework.stereotype.Component;
import run.runnable.kage.command.Command;
import run.runnable.kage.service.LotteryService;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class LotteryCommand implements Command {

    private final LotteryService lotteryService;

    // 匹配: !抽奖 奖品名称 中奖人数 持续时间(分钟)
    private static final Pattern PATTERN = Pattern.compile("(.+?)\\s+(\\d+)\\s+(\\d+)");

    @Override
    public String getName() {
        return "抽奖";
    }

    @Override
    public String getDescription() {
        return "发起抽奖 (用法: !抽奖 奖品 中奖人数 持续分钟)";
    }

    @Override
    public void execute(MessageReceivedEvent event, String[] args) {
        if (!event.isFromGuild()) {
            event.getChannel().sendMessage("❌ 该命令只能在服务器中使用").queue();
            return;
        }

        if (args.length < 3) {
            event.getChannel().sendMessage("用法: `!抽奖 奖品名称 中奖人数 持续分钟`\n例如: `!抽奖 Steam游戏 1 30`").queue();
            return;
        }

        String input = String.join(" ", args);
        Matcher matcher = PATTERN.matcher(input);

        if (!matcher.matches()) {
            event.getChannel().sendMessage("格式错误！用法: `!抽奖 奖品名称 中奖人数 持续分钟`").queue();
            return;
        }

        String prize = matcher.group(1).trim();
        int winnerCount = Integer.parseInt(matcher.group(2));
        int durationMinutes = Integer.parseInt(matcher.group(3));

        if (winnerCount < 1 || winnerCount > 100) {
            event.getChannel().sendMessage("❌ 中奖人数需要在 1-100 之间").queue();
            return;
        }

        if (durationMinutes < 1 || durationMinutes > 10080) { // 最长7天
            event.getChannel().sendMessage("❌ 持续时间需要在 1-10080 分钟之间").queue();
            return;
        }

        LocalDateTime endTime = LocalDateTime.now().plusMinutes(durationMinutes);
        String guildId = event.getGuild().getId();
        String channelId = event.getChannel().getId();
        String creatorId = event.getAuthor().getId();

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

                    event.getChannel().sendMessage(message)
                            .setActionRow(
                                    Button.primary("lottery_join_" + lottery.getId(), "参与抽奖")
                                            .withEmoji(Emoji.fromUnicode("🎉"))
                            )
                            .queue(msg -> {
                                // 保存消息ID
                                lotteryService.updateMessageId(lottery.getId(), msg.getId()).subscribe();
                            });
                });
    }
}
