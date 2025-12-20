package run.runnable.kage.schedule;

import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import run.runnable.kage.dto.LeaderboardEntry;
import run.runnable.kage.service.DiscordBotService;
import run.runnable.kage.service.LeaderboardStatsService;

import java.awt.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 摸鱼排行榜每日播报调度器
 * 每天早上 5:30 自动发送前一天的摸鱼忍者王播报
 */
@Slf4j
@Component
public class LeaderboardBroadcastScheduler {

    private final DiscordBotService discordBotService;
    private final LeaderboardStatsService leaderboardStatsService;
    private final String broadcastChannelName;

    private static final int TOP_LIMIT = 3;
    private static final String[] MEDAL_EMOJIS = {"🥇", "🥈", "🥉"};
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public LeaderboardBroadcastScheduler(
            DiscordBotService discordBotService,
            LeaderboardStatsService leaderboardStatsService,
            @Value("${discord.leaderboard.broadcast-channel:摸鱼排行榜}") String broadcastChannelName) {
        this.discordBotService = discordBotService;
        this.leaderboardStatsService = leaderboardStatsService;
        this.broadcastChannelName = broadcastChannelName;
    }

    /**
     * 每天早上 5:30 执行播报
     */
    @Scheduled(cron = "0 30 5 * * ?")
    public void broadcastDailyWinner() {
        log.info("开始执行每日摸鱼王播报任务...");

        if (!discordBotService.isReady()) {
            log.warn("Discord bot 未就绪，跳过播报");
            return;
        }

        JDA jda = discordBotService.getJda();
        if (jda == null) {
            log.warn("JDA 实例为空，跳过播报");
            return;
        }

        LocalDate yesterday = LocalDate.now().minusDays(1);

        // 遍历所有服务器进行播报
        for (Guild guild : jda.getGuilds()) {
            try {
                broadcastToGuild(guild, yesterday);
            } catch (Exception e) {
                log.error("向服务器 {} 播报失败", guild.getName(), e);
            }
        }

        log.info("每日摸鱼王播报任务执行完成");
    }

    /**
     * 向指定服务器播报
     */
    private void broadcastToGuild(Guild guild, LocalDate date) {
        // 查找播报频道
        List<TextChannel> channels = guild.getTextChannelsByName(broadcastChannelName, true);
        if (channels.isEmpty()) {
            log.warn("服务器 {} 未找到频道 '{}'，跳过播报", guild.getName(), broadcastChannelName);
            return;
        }

        TextChannel channel = channels.get(0);
        String guildId = guild.getId();

        // 查询前一天的排行榜数据
        leaderboardStatsService.getDailyLeaderboard(guildId, date, TOP_LIMIT)
                .collectList()
                .subscribe(
                        entries -> sendBroadcastMessage(channel, entries, date),
                        error -> log.error("查询服务器 {} 排行榜数据失败", guild.getName(), error)
                );
    }

    /**
     * 发送播报消息
     */
    private void sendBroadcastMessage(TextChannel channel, List<LeaderboardEntry> entries, LocalDate date) {
        String dateStr = date.format(DATE_FORMATTER);

        if (entries.isEmpty()) {
            // 无数据时发送提示
            channel.sendMessage("📊 **" + dateStr + " 摸鱼日报**\n\n昨天没有人摸鱼，大家都在认真工作吗？🤔")
                    .queue(
                            msg -> log.info("向频道 {} 发送无数据播报成功", channel.getName()),
                            error -> log.error("向频道 {} 发送播报失败", channel.getName(), error)
                    );
            return;
        }

        // 构建播报 Embed
        EmbedBuilder embed = buildBroadcastEmbed(entries, dateStr);

        channel.sendMessageEmbeds(embed.build())
                .queue(
                        msg -> log.info("向频道 {} 发送摸鱼王播报成功", channel.getName()),
                        error -> log.error("向频道 {} 发送播报失败", channel.getName(), error)
                );
    }

    /**
     * 构建播报 Embed
     */
    private EmbedBuilder buildBroadcastEmbed(List<LeaderboardEntry> entries, String dateStr) {
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("🎉 " + dateStr + " 摸鱼日报")
                .setColor(Color.ORANGE)
                .setFooter("每天早上 5:30 自动播报");

        StringBuilder description = new StringBuilder();
        description.append("昨日摸鱼排行榜出炉啦！\n\n");

        for (LeaderboardEntry entry : entries) {
            String medal = entry.getRank() <= 3 ? MEDAL_EMOJIS[entry.getRank() - 1] : "";
            String userMention = "<@" + entry.getUserId() + ">";

            // 第一名显示"摸鱼忍者王"称号
            if (entry.getRank() == 1) {
                description.append(medal)
                        .append(" ")
                        .append(userMention)
                        .append(" 👑 **摸鱼忍者王**\n")
                        .append("   积分: **")
                        .append(entry.getTotalScore())
                        .append("** | 消息: **")
                        .append(entry.getMessageCount())
                        .append("** 条\n\n");
            } else {
                description.append(medal)
                        .append(" ")
                        .append(userMention)
                        .append(" - **")
                        .append(entry.getTotalScore())
                        .append("** 分 (")
                        .append(entry.getMessageCount())
                        .append(" 条消息)\n");
            }
        }

        description.append("\n恭喜以上摸鱼达人！继续加油摸鱼吧～ 🐟");

        embed.setDescription(description.toString());
        return embed;
    }
}
