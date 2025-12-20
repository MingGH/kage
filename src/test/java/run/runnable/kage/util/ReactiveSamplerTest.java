package run.runnable.kage.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReactiveSamplerTest {

    @Test
    @DisplayName("按计数抽样测试")
    void byCount_shouldSampleCorrectly() {
        AtomicInteger count = new AtomicInteger(0);
        var consumer = ReactiveSampler.byCount(3, (Integer i) -> count.incrementAndGet());

        // Call 6 times, should execute 2 times (at 3rd and 6th call)
        for (int i = 0; i < 6; i++) {
            consumer.accept(i);
        }
        
        assertEquals(2, count.get());
    }

    @Test
    @DisplayName("按条件抽样测试")
    void sampled_shouldRunWhenPredicateIsTrue() {
        AtomicInteger count = new AtomicInteger(0);
        var consumer = ReactiveSampler.sampled((Integer i) -> i % 2 == 0, (Integer i) -> count.incrementAndGet());
        
        consumer.accept(1); // Odd, should skip
        consumer.accept(2); // Even, should run
        consumer.accept(3); // Odd, should skip
        
        assertEquals(1, count.get());
    }
}
