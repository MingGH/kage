package run.runnable.kage.repository;

import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.runnable.kage.domain.Poll;

import java.time.LocalDateTime;

@Repository
public interface PollRepository extends R2dbcRepository<Poll, Long> {

    @Query("SELECT * FROM poll WHERE status = 'ACTIVE' AND end_time <= :now")
    Flux<Poll> findExpiredPolls(LocalDateTime now);

    @Modifying
    @Query("UPDATE poll SET status = :status WHERE id = :id")
    Mono<Void> updateStatus(Long id, String status);

    @Modifying
    @Query("UPDATE poll SET message_id = :messageId WHERE id = :id")
    Mono<Void> updateMessageId(Long id, String messageId);
}
