package run.runnable.kage.repository;

import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import run.runnable.kage.domain.PollOption;

@Repository
public interface PollOptionRepository extends R2dbcRepository<PollOption, Long> {

    @Query("SELECT * FROM poll_option WHERE poll_id = :pollId ORDER BY option_index")
    Flux<PollOption> findByPollId(Long pollId);
}
