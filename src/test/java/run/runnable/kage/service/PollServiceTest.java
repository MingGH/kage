package run.runnable.kage.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import run.runnable.kage.domain.Poll;
import run.runnable.kage.domain.PollOption;
import run.runnable.kage.domain.PollVote;
import run.runnable.kage.repository.PollOptionRepository;
import run.runnable.kage.repository.PollRepository;
import run.runnable.kage.repository.PollVoteRepository;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PollServiceTest {

    @Mock
    private PollRepository pollRepository;
    @Mock
    private PollOptionRepository optionRepository;
    @Mock
    private PollVoteRepository voteRepository;
    @Mock
    private ApplicationContext applicationContext;

    private PollService service;

    @BeforeEach
    void setUp() {
        service = new PollService(pollRepository, optionRepository, voteRepository, applicationContext);
    }

    @Test
    @DisplayName("创建投票")
    void createPoll_shouldSavePollAndOptions() {
        Poll poll = Poll.builder().id(1L).build();
        when(pollRepository.save(any(Poll.class))).thenReturn(Mono.just(poll));
        when(optionRepository.save(any(PollOption.class))).thenReturn(Mono.just(PollOption.builder().build()));

        StepVerifier.create(service.createPoll("g1", "c1", "u1", "title", Arrays.asList("A", "B"), LocalDateTime.now(), false, false))
                .expectNext(poll)
                .verifyComplete();
        
        verify(optionRepository, times(2)).save(any(PollOption.class));
    }

    @Test
    @DisplayName("投票 - 单选 - 首次投票")
    void vote_singleChoice_firstTime() {
        when(voteRepository.findByPollAndUser(anyLong(), anyString())).thenReturn(Flux.empty());
        when(voteRepository.deleteByPollAndUser(anyLong(), anyString())).thenReturn(Mono.empty());
        when(voteRepository.save(any(PollVote.class))).thenReturn(Mono.just(PollVote.builder().build()));

        StepVerifier.create(service.vote(1L, 1L, "u1", "user1", false))
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    @DisplayName("投票 - 再次投票同一选项")
    void vote_sameOption() {
        PollVote existing = PollVote.builder().optionId(1L).build();
        when(voteRepository.findByPollAndUser(anyLong(), anyString())).thenReturn(Flux.just(existing));

        StepVerifier.create(service.vote(1L, 1L, "u1", "user1", false))
                .expectNext(false)
                .verifyComplete();
    }
}
