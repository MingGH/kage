package run.runnable.kage.command.impl;

import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import run.runnable.kage.command.CommandContext;
import run.runnable.kage.command.UnifiedCommand;
import run.runnable.kage.dto.LeaderboardEntry;
import run.runnable.kage.service.LeaderboardStatsService;

import java.awt.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 摸鱼排行榜命令
 * 支持 Slash 命令 /rank [period] 和传统命令 rank [day|week|month|me]
 */
@Component
@RequiredArgsConstructor
public class RankCommand implements UnifiedCommand {

    private final LeaderboardStatsService leaderboardStatsService;

    private static final int LEADERBOARD_LIMIT = 10;
    private static final String[] MEDAL_EMOJIS = {"🥇", "🥈", "🥉"};
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Override
    public String getName() {
        return "rank";
    }

    @Override
    public String getDescription() {
        return "查看摸鱼排行榜和个人积分 (用法: rank [day|week|month|me]，me查看个人统计)";
    }

    @Override
    public CommandData buildCommandData() {
        return Commands.slash("rank", "查看摸鱼排行榜")
                .addOption(OptionType.STRING, "period", "统计周期: day/week/month/me", false);
    }

    @Override
    public void execute(CommandContext ctx) {
        if (!ctx.isFromGuild()) {
            ctx.replyEphemeral("❌ 该命令只能在服务器中使用");
            return;
        }

        String period = parsePeriod(ctx);
        String guildId = ctx.getGuild().getId();
        String userId = ctx.getUser().getId();
        MessageChannel channel = ctx.getChannel();

        ctx.deferReply(hook -> {
            if ("me".equalsIgnoreCase(period)) {
                handlePersonalStats(guildId, userId, hook, channel);
            } else {
                handleLeaderboard(guildId, userId, period, hook, channel);
            }
        });
    }


    /**
     * 解析统计周期参数
     */
    private String parsePeriod(CommandContext ctx) {
        // 尝试从 Slash 命令参数获取
        String slashPeriod = ctx.getString("period");
        if (slashPeriod != null && !slashPeriod.isBlank()) {
            return slashPeriod.toLowerCase();
        }

        // 从传统命令参数解析
        String rawArgs = ctx.getRawArgs();
        if (rawArgs != null && !rawArgs.isBlank()) {
            String arg = rawArgs.trim().toLowerCase();
            if (arg.equals("day") || arg.equals("week") || arg.equals("month") || arg.equals("me")) {
                return arg;
            }
        }

        // 默认返回 day
        return "day";
    }

    /**
     * 处理个人积分查询
     */
    private void handlePersonalStats(String guildId, String userId, CommandContext.ReplyHook hook, MessageChannel channel) {
        leaderboardStatsService.getUserStats(guildId, userId)
                .subscribe(
                        stats -> {
                            if (stats.getTodayScore() == 0 && stats.getWeekScore() == 0 && stats.getMonthScore() == 0) {
                                hook.sendMessage("📊 你还没有摸鱼记录哦，快去发消息摸鱼吧！");
                                return;
                            }

                            EmbedBuilder embed = new EmbedBuilder()
                                    .setTitle("📊 个人摸鱼统计")
                                    .setColor(Color.CYAN)
                                    .setDescription("<@" + userId + "> 的摸鱼数据")
                                    .addField("📅 今日", formatPersonalField(stats.getTodayScore(), stats.getTodayRank(), stats.getTodayMessageCount()), true)
                                    .addField("📆 本周", formatPersonalField(stats.getWeekScore(), stats.getWeekRank(), -1), true)
                                    .addField("🗓️ 本月", formatPersonalField(stats.getMonthScore(), stats.getMonthRank(), -1), true)
                                    .setFooter("继续摸鱼，争取成为摸鱼忍者王！");

                            // 发送 Embed 到频道
                            channel.sendMessageEmbeds(embed.build()).queue(
                                    msg -> hook.sendMessage("✅ 查询完成"),
                                    error -> hook.sendMessage("❌ 发送失败，请稍后重试")
                            );
                        },
                        error -> hook.sendMessage("❌ 查询失败，请稍后重试")
                );
    }

    /**
     * 格式化个人统计字段
     */
    private String formatPersonalField(int score, int rank, int messageCount) {
        StringBuilder sb = new StringBuilder();
        sb.append("积分: **").append(score).append("**\n");
        sb.append("排名: **").append(rank > 0 ? "#" + rank : "暂无").append("**");
        if (messageCount >= 0) {
            sb.append("\n消息: **").append(messageCount).append("** 条");
        }
        return sb.toString();
    }

    /**
     * 处理排行榜查询
     */
    private void handleLeaderboard(String guildId, String userId, String period, CommandContext.ReplyHook hook, MessageChannel channel) {
        Flux<LeaderboardEntry> leaderboardFlux = getLeaderboardByPeriod(guildId, period);

        leaderboardFlux.collectList()
                .zipWith(leaderboardStatsService.getUserRank(guildId, userId, period))
                .subscribe(
                        tuple -> {
                            List<LeaderboardEntry> entries = tuple.getT1();
                            int userRank = tuple.getT2();

                            if (entries.isEmpty()) {
                                hook.sendMessage("📊 暂无数据，快来发消息摸鱼吧！");
                                return;
                            }

                            EmbedBuilder embed = buildLeaderboardEmbed(entries, userId, userRank, period);
                            channel.sendMessageEmbeds(embed.build()).queue(
                                    msg -> hook.sendMessage("✅ 查询完成"),
                                    error -> hook.sendMessage("❌ 发送失败，请稍后重试")
                            );
                        },
                        error -> hook.sendMessage("❌ 查询失败，请稍后重试")
                );
    }


    /**
     * 根据周期获取排行榜数据
     */
    private Flux<LeaderboardEntry> getLeaderboardByPeriod(String guildId, String period) {
        return switch (period.toLowerCase()) {
            case "week" -> leaderboardStatsService.getWeeklyLeaderboard(guildId, LEADERBOARD_LIMIT);
            case "month" -> leaderboardStatsService.getMonthlyLeaderboard(guildId, LEADERBOARD_LIMIT);
            default -> leaderboardStatsService.getDailyLeaderboard(guildId, LocalDate.now(), LEADERBOARD_LIMIT);
        };
    }

    /**
     * 构建排行榜 Embed
     */
    private EmbedBuilder buildLeaderboardEmbed(List<LeaderboardEntry> entries, String userId, int userRank, String period) {
        String periodTitle = getPeriodTitle(period);
        String timeRange = getTimeRange(period);

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("🏆 摸鱼排行榜 - " + periodTitle)
                .setColor(Color.ORANGE)
                .setFooter("统计时间: " + timeRange);

        StringBuilder description = new StringBuilder();

        // 构建排行榜内容
        for (LeaderboardEntry entry : entries) {
            String rankDisplay = getRankDisplay(entry.getRank());
            String userMention = "<@" + entry.getUserId() + ">";
            String highlight = entry.getUserId().equals(userId) ? " ⬅️" : "";

            // 第一名显示"摸鱼忍者王"称号
            String title = "";
            if (entry.getRank() == 1) {
                title = " 👑 **摸鱼忍者王**";
            }

            description.append(rankDisplay)
                    .append(" ")
                    .append(userMention)
                    .append(title)
                    .append(" - **")
                    .append(entry.getTotalScore())
                    .append("** 分 (")
                    .append(entry.getMessageCount())
                    .append(" 条消息)")
                    .append(highlight)
                    .append("\n");
        }

        // 如果用户不在前 10 名，显示用户自己的排名
        boolean userInTop = entries.stream().anyMatch(e -> e.getUserId().equals(userId));
        if (!userInTop && userRank > 0) {
            description.append("\n---\n");
            description.append("你的排名: **#").append(userRank).append("**");
        }

        embed.setDescription(description.toString());
        return embed;
    }

    /**
     * 获取排名显示（前三名使用奖牌 emoji）
     */
    private String getRankDisplay(int rank) {
        if (rank >= 1 && rank <= 3) {
            return MEDAL_EMOJIS[rank - 1];
        }
        return String.format("`%2d`", rank);
    }

    /**
     * 获取周期标题
     */
    private String getPeriodTitle(String period) {
        return switch (period.toLowerCase()) {
            case "week" -> "本周";
            case "month" -> "本月";
            default -> "今日";
        };
    }

    /**
     * 获取统计时间范围描述
     */
    private String getTimeRange(String period) {
        LocalDate today = LocalDate.now();
        return switch (period.toLowerCase()) {
            case "week" -> {
                LocalDate weekStart = today.with(java.time.DayOfWeek.MONDAY);
                yield weekStart.format(DATE_FORMATTER) + " ~ " + today.format(DATE_FORMATTER);
            }
            case "month" -> {
                LocalDate monthStart = today.withDayOfMonth(1);
                yield monthStart.format(DATE_FORMATTER) + " ~ " + today.format(DATE_FORMATTER);
            }
            default -> today.format(DATE_FORMATTER);
        };
    }

}
