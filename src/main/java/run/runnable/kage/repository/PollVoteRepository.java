package run.runnable.kage.repository;

import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.runnable.kage.domain.PollVote;

@Repository
public interface PollVoteRepository extends R2dbcRepository<PollVote, Long> {

    @Query("SELECT * FROM poll_vote WHERE poll_id = :pollId AND user_id = :userId")
    Flux<PollVote> findByPollAndUser(Long pollId, String userId);

    @Query("SELECT COUNT(*) FROM poll_vote WHERE option_id = :optionId")
    Mono<Long> countByOptionId(Long optionId);

    @Modifying
    @Query("DELETE FROM poll_vote WHERE poll_id = :pollId AND user_id = :userId")
    Mono<Void> deleteByPollAndUser(Long pollId, String userId);
}
