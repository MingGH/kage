package run.runnable.kage.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ReactorUtilTest {

    @Test
    @DisplayName("Flux计时测试")
    void timeFlux_shouldRecordTime() {
        AtomicLong recordedTime = new AtomicLong(-1);
        
        Flux<String> flux = Flux.just("a", "b").map(s -> {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                // ignore
            }
            return s;
        });
        
        StepVerifier.create(ReactorUtil.timeFlux(flux, recordedTime::set))
                .expectNext("a")
                .expectNext("b")
                .verifyComplete();
        
        assertTrue(recordedTime.get() >= 0);
    }

    @Test
    @DisplayName("Mono计时测试")
    void timeMono_shouldRecordTime() {
        AtomicLong recordedTime = new AtomicLong(-1);
        
        Mono<String> mono = Mono.just("a").map(s -> {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                // ignore
            }
            return s;
        });
        
        StepVerifier.create(ReactorUtil.timeMono(mono, recordedTime::set))
                .expectNext("a")
                .verifyComplete();
        
        assertTrue(recordedTime.get() >= 0);
    }

    @Test
    @DisplayName("获取随机元素测试")
    void getRandomElement_shouldReturnOneElement() {
        Flux<Integer> flux = Flux.range(1, 10);
        
        StepVerifier.create(ReactorUtil.getRandomElement(flux))
                .expectNextCount(1)
                .verifyComplete();
        
        StepVerifier.create(ReactorUtil.getRandomElement(Flux.empty()))
                .verifyComplete();
    }
}
