package run.runnable.kage.repository;

import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.runnable.kage.domain.LotteryParticipant;

@Repository
public interface LotteryParticipantRepository extends R2dbcRepository<LotteryParticipant, Long> {

    @Query("SELECT * FROM lottery_participant WHERE lottery_id = :lotteryId")
    Flux<LotteryParticipant> findByLotteryId(Long lotteryId);

    @Query("SELECT COUNT(*) FROM lottery_participant WHERE lottery_id = :lotteryId")
    Mono<Long> countByLotteryId(Long lotteryId);

    @Query("SELECT * FROM lottery_participant WHERE lottery_id = :lotteryId AND user_id = :userId")
    Mono<LotteryParticipant> findByLotteryAndUser(Long lotteryId, String userId);
}
