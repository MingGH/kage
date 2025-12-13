package run.runnable.kage.repository;

import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;
import run.runnable.kage.domain.CetWord;

@Repository
public interface CetWordRepository extends R2dbcRepository<CetWord, Integer> {

    @Query("SELECT id, data::text AS data FROM cet_words ORDER BY random() LIMIT 1")
    Mono<CetWord> findRandomOne();
}