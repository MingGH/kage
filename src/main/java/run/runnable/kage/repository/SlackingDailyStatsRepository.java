package run.runnable.kage.repository;

import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.runnable.kage.domain.SlackingDailyStats;

import java.time.LocalDate;

@Repository
public interface SlackingDailyStatsRepository extends R2dbcRepository<SlackingDailyStats, Long> {

    /**
     * 查找指定服务器、用户和日期的统计记录
     */
    @Query("SELECT * FROM slacking_daily_stats WHERE guild_id = :guildId AND user_id = :userId AND stat_date = :statDate")
    Mono<SlackingDailyStats> findByGuildIdAndUserIdAndStatDate(String guildId, String userId, LocalDate statDate);

    /**
     * Upsert 操作：更新或插入统计记录
     * 如果记录存在则累加消息数和积分，否则插入新记录
     */
    @Modifying
    @Query("""
        INSERT INTO slacking_daily_stats (guild_id, user_id, user_name, stat_date, message_count, total_score, created_at, updated_at)
        VALUES (:guildId, :userId, :userName, :statDate, :messageCount, :score, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
        ON CONFLICT (guild_id, user_id, stat_date)
        DO UPDATE SET
            message_count = slacking_daily_stats.message_count + :messageCount,
            total_score = slacking_daily_stats.total_score + :score,
            user_name = :userName,
            updated_at = CURRENT_TIMESTAMP
        """)
    Mono<Void> upsertStats(String guildId, String userId, String userName, LocalDate statDate, int messageCount, int score);

    /**
     * 获取指定日期的排行榜（按积分降序，相同积分按消息数降序）
     */
    @Query("""
        SELECT * FROM slacking_daily_stats
        WHERE guild_id = :guildId AND stat_date = :statDate
        ORDER BY total_score DESC, message_count DESC
        LIMIT :limit
        """)
    Flux<SlackingDailyStats> findDailyLeaderboard(String guildId, LocalDate statDate, int limit);

    /**
     * 获取日期范围内的汇总排行榜（周榜、月榜）
     * 按用户汇总积分和消息数
     */
    @Query("""
        SELECT 
            NULL as id,
            guild_id,
            user_id,
            MAX(user_name) as user_name,
            :endDate as stat_date,
            SUM(message_count) as message_count,
            SUM(total_score) as total_score,
            MIN(created_at) as created_at,
            MAX(updated_at) as updated_at
        FROM slacking_daily_stats
        WHERE guild_id = :guildId AND stat_date BETWEEN :startDate AND :endDate
        GROUP BY guild_id, user_id
        ORDER BY total_score DESC, message_count DESC
        LIMIT :limit
        """)
    Flux<SlackingDailyStats> findLeaderboardByDateRange(String guildId, LocalDate startDate, LocalDate endDate, int limit);

    /**
     * 获取用户在指定日期的排名
     */
    @Query("""
        SELECT COUNT(*) + 1 FROM slacking_daily_stats
        WHERE guild_id = :guildId AND stat_date = :statDate
        AND (total_score > (SELECT COALESCE(total_score, 0) FROM slacking_daily_stats WHERE guild_id = :guildId AND user_id = :userId AND stat_date = :statDate)
             OR (total_score = (SELECT COALESCE(total_score, 0) FROM slacking_daily_stats WHERE guild_id = :guildId AND user_id = :userId AND stat_date = :statDate)
                 AND message_count > (SELECT COALESCE(message_count, 0) FROM slacking_daily_stats WHERE guild_id = :guildId AND user_id = :userId AND stat_date = :statDate)))
        """)
    Mono<Integer> findUserDailyRank(String guildId, String userId, LocalDate statDate);

    /**
     * 获取用户在日期范围内的排名（周榜、月榜）
     */
    @Query("""
        WITH user_totals AS (
            SELECT user_id, SUM(total_score) as total_score, SUM(message_count) as message_count
            FROM slacking_daily_stats
            WHERE guild_id = :guildId AND stat_date BETWEEN :startDate AND :endDate
            GROUP BY user_id
        ),
        target_user AS (
            SELECT total_score, message_count FROM user_totals WHERE user_id = :userId
        )
        SELECT COUNT(*) + 1 FROM user_totals
        WHERE total_score > COALESCE((SELECT total_score FROM target_user), 0)
           OR (total_score = COALESCE((SELECT total_score FROM target_user), 0)
               AND message_count > COALESCE((SELECT message_count FROM target_user), 0))
        """)
    Mono<Integer> findUserRankByDateRange(String guildId, String userId, LocalDate startDate, LocalDate endDate);

    /**
     * 获取用户在日期范围内的汇总统计
     */
    @Query("""
        SELECT 
            NULL as id,
            guild_id,
            user_id,
            MAX(user_name) as user_name,
            :endDate as stat_date,
            SUM(message_count) as message_count,
            SUM(total_score) as total_score,
            MIN(created_at) as created_at,
            MAX(updated_at) as updated_at
        FROM slacking_daily_stats
        WHERE guild_id = :guildId AND user_id = :userId AND stat_date BETWEEN :startDate AND :endDate
        GROUP BY guild_id, user_id
        """)
    Mono<SlackingDailyStats> findUserStatsByDateRange(String guildId, String userId, LocalDate startDate, LocalDate endDate);
}
