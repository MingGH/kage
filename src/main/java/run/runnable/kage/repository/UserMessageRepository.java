package run.runnable.kage.repository;

import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.runnable.kage.domain.UserMessage;

import java.time.LocalDateTime;

@Repository
public interface UserMessageRepository extends R2dbcRepository<UserMessage, Long> {

    /**
     * 统计用户在指定服务器的消息数
     */
    @Query("SELECT COUNT(*) FROM user_message WHERE guild_id = :guildId AND user_id = :userId")
    Mono<Long> countByGuildAndUser(String guildId, String userId);

    /**
     * 获取服务器指定时间范围内的消息
     */
    @Query("SELECT * FROM user_message WHERE guild_id = :guildId AND created_at BETWEEN :start AND :end ORDER BY created_at")
    Flux<UserMessage> findByGuildAndDateRange(String guildId, LocalDateTime start, LocalDateTime end);
}
