package run.runnable.kage.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import run.runnable.kage.domain.SlackingDailyStats;
import run.runnable.kage.dto.LeaderboardEntry;
import run.runnable.kage.dto.UserStats;
import run.runnable.kage.repository.SlackingDailyStatsRepository;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * LeaderboardStatsService 单元测试
 * 覆盖排行榜排序、周/月日期范围计算、用户排名查找
 */
@ExtendWith(MockitoExtension.class)
class LeaderboardStatsServiceTest {

    @Mock
    private SlackingDailyStatsRepository statsRepository;

    @Mock
    private ScoreCalculator scoreCalculator;

    private LeaderboardStatsService leaderboardStatsService;

    private static final String GUILD_ID = "test-guild-123";
    private static final String USER_ID_1 = "user-1";
    private static final String USER_ID_2 = "user-2";
    private static final String USER_ID_3 = "user-3";

    @BeforeEach
    void setUp() {
        leaderboardStatsService = new LeaderboardStatsService(statsRepository, scoreCalculator);
    }

    // ========== 排行榜排序测试 ==========

    @Test
    @DisplayName("日榜应按积分降序排列并正确分配排名")
    void getDailyLeaderboard_shouldReturnSortedByScoreDesc() {
        LocalDate today = LocalDate.now();
        
        // 模拟数据库返回已排序的数据
        when(statsRepository.findDailyLeaderboard(GUILD_ID, today, 10))
                .thenReturn(Flux.just(
                        createStats(USER_ID_1, "User1", 10, 50),
                        createStats(USER_ID_2, "User2", 8, 40),
                        createStats(USER_ID_3, "User3", 5, 20)
                ));

        StepVerifier.create(leaderboardStatsService.getDailyLeaderboard(GUILD_ID, today, 10))
                .assertNext(entry -> {
                    assertEquals(1, entry.getRank());
                    assertEquals(USER_ID_1, entry.getUserId());
                    assertEquals(50, entry.getTotalScore());
                })
                .assertNext(entry -> {
                    assertEquals(2, entry.getRank());
                    assertEquals(USER_ID_2, entry.getUserId());
                    assertEquals(40, entry.getTotalScore());
                })
                .assertNext(entry -> {
                    assertEquals(3, entry.getRank());
                    assertEquals(USER_ID_3, entry.getUserId());
                    assertEquals(20, entry.getTotalScore());
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("空排行榜应返回空流")
    void getDailyLeaderboard_emptyData_shouldReturnEmptyFlux() {
        LocalDate today = LocalDate.now();
        
        when(statsRepository.findDailyLeaderboard(GUILD_ID, today, 10))
                .thenReturn(Flux.empty());

        StepVerifier.create(leaderboardStatsService.getDailyLeaderboard(GUILD_ID, today, 10))
                .verifyComplete();
    }

    // ========== 周榜日期范围测试 ==========

    @Test
    @DisplayName("周榜应使用本周一至今的日期范围")
    void getWeeklyLeaderboard_shouldUseCorrectDateRange() {
        LocalDate today = LocalDate.now();
        LocalDate weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        
        when(statsRepository.findLeaderboardByDateRange(eq(GUILD_ID), eq(weekStart), eq(today), eq(10)))
                .thenReturn(Flux.just(
                        createStats(USER_ID_1, "User1", 30, 150)
                ));

        StepVerifier.create(leaderboardStatsService.getWeeklyLeaderboard(GUILD_ID, 10))
                .assertNext(entry -> {
                    assertEquals(1, entry.getRank());
                    assertEquals(150, entry.getTotalScore());
                })
                .verifyComplete();
    }

    // ========== 月榜日期范围测试 ==========

    @Test
    @DisplayName("月榜应使用本月1日至今的日期范围")
    void getMonthlyLeaderboard_shouldUseCorrectDateRange() {
        LocalDate today = LocalDate.now();
        LocalDate monthStart = today.with(TemporalAdjusters.firstDayOfMonth());
        
        when(statsRepository.findLeaderboardByDateRange(eq(GUILD_ID), eq(monthStart), eq(today), eq(10)))
                .thenReturn(Flux.just(
                        createStats(USER_ID_1, "User1", 100, 500)
                ));

        StepVerifier.create(leaderboardStatsService.getMonthlyLeaderboard(GUILD_ID, 10))
                .assertNext(entry -> {
                    assertEquals(1, entry.getRank());
                    assertEquals(500, entry.getTotalScore());
                })
                .verifyComplete();
    }

    // ========== 用户排名查找测试 ==========

    @Test
    @DisplayName("getUserRank 日榜应返回正确排名")
    void getUserRank_day_shouldReturnCorrectRank() {
        LocalDate today = LocalDate.now();
        
        when(statsRepository.findUserDailyRank(GUILD_ID, USER_ID_1, today))
                .thenReturn(Mono.just(3));

        StepVerifier.create(leaderboardStatsService.getUserRank(GUILD_ID, USER_ID_1, "day"))
                .expectNext(3)
                .verifyComplete();
    }

    @Test
    @DisplayName("getUserRank 周榜应返回正确排名")
    void getUserRank_week_shouldReturnCorrectRank() {
        LocalDate today = LocalDate.now();
        LocalDate weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        
        when(statsRepository.findUserRankByDateRange(GUILD_ID, USER_ID_1, weekStart, today))
                .thenReturn(Mono.just(5));

        StepVerifier.create(leaderboardStatsService.getUserRank(GUILD_ID, USER_ID_1, "week"))
                .expectNext(5)
                .verifyComplete();
    }

    @Test
    @DisplayName("getUserRank 月榜应返回正确排名")
    void getUserRank_month_shouldReturnCorrectRank() {
        LocalDate today = LocalDate.now();
        LocalDate monthStart = today.with(TemporalAdjusters.firstDayOfMonth());
        
        when(statsRepository.findUserRankByDateRange(GUILD_ID, USER_ID_1, monthStart, today))
                .thenReturn(Mono.just(2));

        StepVerifier.create(leaderboardStatsService.getUserRank(GUILD_ID, USER_ID_1, "month"))
                .expectNext(2)
                .verifyComplete();
    }

    @Test
    @DisplayName("getUserRank 用户无记录时应返回 0")
    void getUserRank_noRecord_shouldReturnZero() {
        LocalDate today = LocalDate.now();
        
        when(statsRepository.findUserDailyRank(GUILD_ID, USER_ID_1, today))
                .thenReturn(Mono.empty());

        StepVerifier.create(leaderboardStatsService.getUserRank(GUILD_ID, USER_ID_1, "day"))
                .expectNext(0)
                .verifyComplete();
    }

    // ========== 用户统计查询测试 ==========

    @Test
    @DisplayName("getUserStats 应返回完整的用户统计信息")
    void getUserStats_shouldReturnCompleteStats() {
        LocalDate today = LocalDate.now();
        LocalDate weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate monthStart = today.with(TemporalAdjusters.firstDayOfMonth());

        // 模拟今日统计
        when(statsRepository.findByGuildIdAndUserIdAndStatDate(GUILD_ID, USER_ID_1, today))
                .thenReturn(Mono.just(createStats(USER_ID_1, "TestUser", 5, 15)));
        
        // 模拟周统计
        when(statsRepository.findUserStatsByDateRange(GUILD_ID, USER_ID_1, weekStart, today))
                .thenReturn(Mono.just(createStats(USER_ID_1, "TestUser", 20, 60)));
        
        // 模拟月统计
        when(statsRepository.findUserStatsByDateRange(GUILD_ID, USER_ID_1, monthStart, today))
                .thenReturn(Mono.just(createStats(USER_ID_1, "TestUser", 80, 240)));

        // 模拟排名
        when(statsRepository.findUserDailyRank(GUILD_ID, USER_ID_1, today))
                .thenReturn(Mono.just(3));
        when(statsRepository.findUserRankByDateRange(GUILD_ID, USER_ID_1, weekStart, today))
                .thenReturn(Mono.just(5));
        when(statsRepository.findUserRankByDateRange(GUILD_ID, USER_ID_1, monthStart, today))
                .thenReturn(Mono.just(2));

        StepVerifier.create(leaderboardStatsService.getUserStats(GUILD_ID, USER_ID_1))
                .assertNext(stats -> {
                    assertEquals(USER_ID_1, stats.getUserId());
                    assertEquals(15, stats.getTodayScore());
                    assertEquals(5, stats.getTodayMessageCount());
                    assertEquals(3, stats.getTodayRank());
                    assertEquals(60, stats.getWeekScore());
                    assertEquals(5, stats.getWeekRank());
                    assertEquals(240, stats.getMonthScore());
                    assertEquals(2, stats.getMonthRank());
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("getUserStats 用户无记录时应返回零值统计")
    void getUserStats_noRecord_shouldReturnZeroStats() {
        LocalDate today = LocalDate.now();
        LocalDate weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate monthStart = today.with(TemporalAdjusters.firstDayOfMonth());

        // 模拟无记录
        when(statsRepository.findByGuildIdAndUserIdAndStatDate(GUILD_ID, USER_ID_1, today))
                .thenReturn(Mono.empty());
        when(statsRepository.findUserStatsByDateRange(GUILD_ID, USER_ID_1, weekStart, today))
                .thenReturn(Mono.empty());
        when(statsRepository.findUserStatsByDateRange(GUILD_ID, USER_ID_1, monthStart, today))
                .thenReturn(Mono.empty());
        when(statsRepository.findUserDailyRank(GUILD_ID, USER_ID_1, today))
                .thenReturn(Mono.empty());
        when(statsRepository.findUserRankByDateRange(GUILD_ID, USER_ID_1, weekStart, today))
                .thenReturn(Mono.empty());
        when(statsRepository.findUserRankByDateRange(GUILD_ID, USER_ID_1, monthStart, today))
                .thenReturn(Mono.empty());

        StepVerifier.create(leaderboardStatsService.getUserStats(GUILD_ID, USER_ID_1))
                .assertNext(stats -> {
                    assertEquals(USER_ID_1, stats.getUserId());
                    assertEquals(0, stats.getTodayScore());
                    assertEquals(0, stats.getTodayMessageCount());
                    assertEquals(0, stats.getTodayRank());
                    assertEquals(0, stats.getWeekScore());
                    assertEquals(0, stats.getWeekRank());
                    assertEquals(0, stats.getMonthScore());
                    assertEquals(0, stats.getMonthRank());
                })
                .verifyComplete();
    }

    // ========== 消息记录测试 ==========

    @Test
    @DisplayName("recordMessage 应计算积分并调用 upsert")
    void recordMessage_shouldCalculateScoreAndUpsert() {
        String content = "Hello, this is a test message!";
        LocalDate today = LocalDate.now();
        
        when(scoreCalculator.calculateScore(content)).thenReturn(2);
        when(statsRepository.upsertStats(eq(GUILD_ID), eq(USER_ID_1), eq("TestUser"), eq(today), eq(1), eq(2)))
                .thenReturn(Mono.empty());

        StepVerifier.create(leaderboardStatsService.recordMessage(GUILD_ID, USER_ID_1, "TestUser", content))
                .verifyComplete();
    }

    // ========== 辅助方法 ==========

    private SlackingDailyStats createStats(String userId, String userName, int messageCount, int totalScore) {
        return SlackingDailyStats.builder()
                .guildId(GUILD_ID)
                .userId(userId)
                .userName(userName)
                .statDate(LocalDate.now())
                .messageCount(messageCount)
                .totalScore(totalScore)
                .build();
    }
}
