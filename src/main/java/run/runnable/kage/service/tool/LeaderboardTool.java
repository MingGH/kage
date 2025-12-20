package run.runnable.kage.service.tool;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import run.runnable.kage.dto.LeaderboardEntry;
import run.runnable.kage.dto.UserStats;
import run.runnable.kage.service.LeaderboardStatsService;

import java.time.LocalDate;
import java.util.List;

/**
 * AI 工具：查询摸鱼积分和排行榜
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LeaderboardTool {

    private final LeaderboardStatsService leaderboardStatsService;

    @Tool(description = "查询用户的摸鱼积分和排名。当用户询问'我的积分'、'我排第几'、'我的摸鱼数据'等问题时使用此工具。")
    public String getUserScore(
            @ToolParam(description = "服务器ID") String guildId,
            @ToolParam(description = "用户ID") String userId
    ) {
        log.info("查询用户积分: guildId={}, userId={}", guildId, userId);
        
        try {
            UserStats stats = leaderboardStatsService.getUserStats(guildId, userId).block();
            
            if (stats == null || (stats.getTodayScore() == 0 && stats.getWeekScore() == 0 && stats.getMonthScore() == 0)) {
                return "该用户还没有摸鱼记录，快去发消息摸鱼吧！";
            }
            
            StringBuilder sb = new StringBuilder();
            sb.append("用户摸鱼统计：\n");
            sb.append("- 今日：").append(stats.getTodayScore()).append(" 分");
            if (stats.getTodayRank() > 0) {
                sb.append("，排名第 ").append(stats.getTodayRank()).append(" 名");
            }
            sb.append("，发送 ").append(stats.getTodayMessageCount()).append(" 条消息\n");
            
            sb.append("- 本周：").append(stats.getWeekScore()).append(" 分");
            if (stats.getWeekRank() > 0) {
                sb.append("，排名第 ").append(stats.getWeekRank()).append(" 名");
            }
            sb.append("\n");
            
            sb.append("- 本月：").append(stats.getMonthScore()).append(" 分");
            if (stats.getMonthRank() > 0) {
                sb.append("，排名第 ").append(stats.getMonthRank()).append(" 名");
            }
            
            return sb.toString();
        } catch (Exception e) {
            log.error("查询用户积分失败: {}", e.getMessage());
            return "查询失败，请稍后重试";
        }
    }

    @Tool(description = "查询摸鱼排行榜。当用户询问'排行榜'、'谁摸鱼最多'、'今日/本周/本月排名'等问题时使用此工具。")
    public String getLeaderboard(
            @ToolParam(description = "服务器ID") String guildId,
            @ToolParam(description = "统计周期：day(今日)、week(本周)、month(本月)") String period
    ) {
        log.info("查询排行榜: guildId={}, period={}", guildId, period);
        
        try {
            List<LeaderboardEntry> entries = switch (period.toLowerCase()) {
                case "week" -> leaderboardStatsService.getWeeklyLeaderboard(guildId, 10).collectList().block();
                case "month" -> leaderboardStatsService.getMonthlyLeaderboard(guildId, 10).collectList().block();
                default -> leaderboardStatsService.getDailyLeaderboard(guildId, LocalDate.now(), 10).collectList().block();
            };
            
            if (entries == null || entries.isEmpty()) {
                return "暂无排行榜数据";
            }
            
            String periodName = switch (period.toLowerCase()) {
                case "week" -> "本周";
                case "month" -> "本月";
                default -> "今日";
            };
            
            StringBuilder sb = new StringBuilder();
            sb.append(periodName).append("摸鱼排行榜 TOP ").append(entries.size()).append("：\n");
            
            String[] medals = {"🥇", "🥈", "🥉"};
            for (LeaderboardEntry entry : entries) {
                String rankDisplay = entry.getRank() <= 3 ? medals[entry.getRank() - 1] : entry.getRank() + ".";
                sb.append(rankDisplay).append(" ")
                  .append(entry.getUserName())
                  .append(" - ").append(entry.getTotalScore()).append(" 分")
                  .append(" (").append(entry.getMessageCount()).append(" 条消息)\n");
            }
            
            return sb.toString();
        } catch (Exception e) {
            log.error("查询排行榜失败: {}", e.getMessage());
            return "查询失败，请稍后重试";
        }
    }
}
