package run.runnable.kage.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import run.runnable.kage.domain.Lottery;
import run.runnable.kage.domain.LotteryParticipant;
import run.runnable.kage.repository.LotteryParticipantRepository;
import run.runnable.kage.repository.LotteryRepository;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class LotteryServiceTest {

    @Mock
    private LotteryRepository lotteryRepository;
    @Mock
    private LotteryParticipantRepository participantRepository;
    @Mock
    private ApplicationContext applicationContext;

    private LotteryService service;

    @BeforeEach
    void setUp() {
        service = new LotteryService(lotteryRepository, participantRepository, applicationContext);
    }

    @Test
    @DisplayName("创建抽奖")
    void createLottery_shouldSave() {
        Lottery lottery = Lottery.builder().id(1L).build();
        when(lotteryRepository.save(any(Lottery.class))).thenReturn(Mono.just(lottery));

        StepVerifier.create(service.createLottery("g1", "c1", "u1", "prize", 1, LocalDateTime.now()))
                .expectNext(lottery)
                .verifyComplete();
    }

    @Test
    @DisplayName("参与抽奖 - 首次参与")
    void participate_firstTime() {
        when(participantRepository.findByLotteryAndUser(anyLong(), anyString())).thenReturn(Mono.empty());
        
        LotteryParticipant participant = LotteryParticipant.builder().id(1L).build();
        when(participantRepository.save(any(LotteryParticipant.class))).thenReturn(Mono.just(participant));

        StepVerifier.create(service.participate(1L, "u1", "user1"))
                .expectNext(participant)
                .verifyComplete();
    }

    @Test
    @DisplayName("参与抽奖 - 已参与")
    void participate_alreadyJoined() {
        LotteryParticipant existing = LotteryParticipant.builder().id(1L).userId("u1").build();
        when(participantRepository.findByLotteryAndUser(anyLong(), anyString())).thenReturn(Mono.just(existing));
        // Avoid NPE in switchIfEmpty evaluation
        lenient().when(participantRepository.save(any(LotteryParticipant.class))).thenReturn(Mono.empty());

        StepVerifier.create(service.participate(1L, "u1", "user1"))
                .expectNext(existing)
                .verifyComplete();
    }
}
