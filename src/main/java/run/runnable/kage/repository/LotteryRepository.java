package run.runnable.kage.repository;

import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.runnable.kage.domain.Lottery;

import java.time.LocalDateTime;

@Repository
public interface LotteryRepository extends R2dbcRepository<Lottery, Long> {

    @Query("SELECT * FROM lottery WHERE status = 'ACTIVE' AND end_time <= :now")
    Flux<Lottery> findExpiredLotteries(LocalDateTime now);

    @Modifying
    @Query("UPDATE lottery SET status = :status WHERE id = :id")
    Mono<Void> updateStatus(Long id, String status);

    @Modifying
    @Query("UPDATE lottery SET message_id = :messageId WHERE id = :id")
    Mono<Void> updateMessageId(Long id, String messageId);
}
