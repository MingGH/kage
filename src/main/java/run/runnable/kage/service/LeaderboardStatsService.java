package run.runnable.kage.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.runnable.kage.domain.SlackingDailyStats;
import run.runnable.kage.dto.LeaderboardEntry;
import run.runnable.kage.dto.UserStats;
import run.runnable.kage.repository.SlackingDailyStatsRepository;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 摸鱼排行榜统计服务
 * 负责统计数据的更新和查询
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LeaderboardStatsService {

    private final SlackingDailyStatsRepository statsRepository;
    private final ScoreCalculator scoreCalculator;

    /**
     * 记录用户消息并更新统计
     * 
     * @param guildId  服务器 ID
     * @param userId   用户 ID
     * @param userName 用户名
     * @param content  消息内容
     * @return Mono<Void>
     */
    public Mono<Void> recordMessage(String guildId, String userId, String userName, String content) {
        int score = scoreCalculator.calculateScore(content);
        LocalDate today = LocalDate.now();
        
        return statsRepository.upsertStats(guildId, userId, userName, today, 1, score)
                .doOnSuccess(v -> log.debug("记录消息统计: guildId={}, userId={}, score={}", guildId, userId, score))
                .doOnError(e -> log.error("记录消息统计失败: guildId={}, userId={}", guildId, userId, e));
    }

    /**
     * 获取日榜排行
     * 
     * @param guildId 服务器 ID
     * @param date    日期
     * @param limit   返回数量限制
     * @return 排行榜条目流
     */
    public Flux<LeaderboardEntry> getDailyLeaderboard(String guildId, LocalDate date, int limit) {
        AtomicInteger rankCounter = new AtomicInteger(0);
        return statsRepository.findDailyLeaderboard(guildId, date, limit)
                .map(stats -> toLeaderboardEntry(stats, rankCounter.incrementAndGet()));
    }

    /**
     * 获取周榜排行
     * 
     * @param guildId 服务器 ID
     * @param limit   返回数量限制
     * @return 排行榜条目流
     */
    public Flux<LeaderboardEntry> getWeeklyLeaderboard(String guildId, int limit) {
        LocalDate today = LocalDate.now();
        LocalDate weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        return getLeaderboardByDateRange(guildId, weekStart, today, limit);
    }

    /**
     * 获取月榜排行
     * 
     * @param guildId 服务器 ID
     * @param limit   返回数量限制
     * @return 排行榜条目流
     */
    public Flux<LeaderboardEntry> getMonthlyLeaderboard(String guildId, int limit) {
        LocalDate today = LocalDate.now();
        LocalDate monthStart = today.with(TemporalAdjusters.firstDayOfMonth());
        return getLeaderboardByDateRange(guildId, monthStart, today, limit);
    }

    /**
     * 获取指定日期范围的排行榜
     */
    private Flux<LeaderboardEntry> getLeaderboardByDateRange(String guildId, LocalDate startDate, LocalDate endDate, int limit) {
        AtomicInteger rankCounter = new AtomicInteger(0);
        return statsRepository.findLeaderboardByDateRange(guildId, startDate, endDate, limit)
                .map(stats -> toLeaderboardEntry(stats, rankCounter.incrementAndGet()));
    }

    /**
     * 获取用户个人统计
     * 
     * @param guildId 服务器 ID
     * @param userId  用户 ID
     * @return 用户统计信息
     */
    public Mono<UserStats> getUserStats(String guildId, String userId) {
        LocalDate today = LocalDate.now();
        LocalDate weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate monthStart = today.with(TemporalAdjusters.firstDayOfMonth());

        // 并行查询今日、本周、本月的统计数据和排名
        Mono<SlackingDailyStats> todayStats = statsRepository.findByGuildIdAndUserIdAndStatDate(guildId, userId, today)
                .defaultIfEmpty(emptyStats(guildId, userId));
        Mono<SlackingDailyStats> weekStats = statsRepository.findUserStatsByDateRange(guildId, userId, weekStart, today)
                .defaultIfEmpty(emptyStats(guildId, userId));
        Mono<SlackingDailyStats> monthStats = statsRepository.findUserStatsByDateRange(guildId, userId, monthStart, today)
                .defaultIfEmpty(emptyStats(guildId, userId));

        Mono<Integer> todayRank = statsRepository.findUserDailyRank(guildId, userId, today)
                .defaultIfEmpty(0);
        Mono<Integer> weekRank = statsRepository.findUserRankByDateRange(guildId, userId, weekStart, today)
                .defaultIfEmpty(0);
        Mono<Integer> monthRank = statsRepository.findUserRankByDateRange(guildId, userId, monthStart, today)
                .defaultIfEmpty(0);

        return Mono.zip(todayStats, weekStats, monthStats, todayRank, weekRank, monthRank)
                .map(tuple -> UserStats.builder()
                        .userId(userId)
                        .userName(tuple.getT1().getUserName())
                        .todayScore(tuple.getT1().getTotalScore() != null ? tuple.getT1().getTotalScore() : 0)
                        .todayMessageCount(tuple.getT1().getMessageCount() != null ? tuple.getT1().getMessageCount() : 0)
                        .todayRank(tuple.getT4())
                        .weekScore(tuple.getT2().getTotalScore() != null ? tuple.getT2().getTotalScore() : 0)
                        .weekRank(tuple.getT5())
                        .monthScore(tuple.getT3().getTotalScore() != null ? tuple.getT3().getTotalScore() : 0)
                        .monthRank(tuple.getT6())
                        .build());
    }

    /**
     * 获取用户在指定排行榜中的排名
     * 
     * @param guildId 服务器 ID
     * @param userId  用户 ID
     * @param period  统计周期 (day/week/month)
     * @return 用户排名
     */
    public Mono<Integer> getUserRank(String guildId, String userId, String period) {
        LocalDate today = LocalDate.now();
        
        return switch (period.toLowerCase()) {
            case "week" -> {
                LocalDate weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                yield statsRepository.findUserRankByDateRange(guildId, userId, weekStart, today)
                        .defaultIfEmpty(0);
            }
            case "month" -> {
                LocalDate monthStart = today.with(TemporalAdjusters.firstDayOfMonth());
                yield statsRepository.findUserRankByDateRange(guildId, userId, monthStart, today)
                        .defaultIfEmpty(0);
            }
            default -> statsRepository.findUserDailyRank(guildId, userId, today)
                    .defaultIfEmpty(0);
        };
    }

    /**
     * 将 SlackingDailyStats 转换为 LeaderboardEntry
     */
    private LeaderboardEntry toLeaderboardEntry(SlackingDailyStats stats, int rank) {
        return LeaderboardEntry.builder()
                .rank(rank)
                .userId(stats.getUserId())
                .userName(stats.getUserName())
                .messageCount(stats.getMessageCount() != null ? stats.getMessageCount() : 0)
                .totalScore(stats.getTotalScore() != null ? stats.getTotalScore() : 0)
                .build();
    }

    /**
     * 创建空的统计对象
     */
    private SlackingDailyStats emptyStats(String guildId, String userId) {
        return SlackingDailyStats.builder()
                .guildId(guildId)
                .userId(userId)
                .messageCount(0)
                .totalScore(0)
                .build();
    }
}
