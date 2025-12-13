package run.runnable.kage.repository;

import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.runnable.kage.domain.ChatMessage;

@Repository
public interface ChatMessageRepository extends R2dbcRepository<ChatMessage, Long> {

    /**
     * 获取用户在指定服务器的最近对话历史
     */
    @Query("SELECT * FROM chat_message WHERE guild_id = :guildId AND user_id = :userId AND deleted = FALSE ORDER BY created_at DESC LIMIT :limit")
    Flux<ChatMessage> findRecentByGuildAndUser(String guildId, String userId, int limit);

    /**
     * 软删除用户在指定服务器的对话历史
     */
    @Modifying
    @Query("UPDATE chat_message SET deleted = TRUE WHERE guild_id = :guildId AND user_id = :userId AND deleted = FALSE")
    Mono<Void> softDeleteByGuildAndUser(String guildId, String userId);
}
