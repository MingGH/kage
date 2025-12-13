package run.runnable.kage.util;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * 用于 WebFlux 场景的日志或副作用抽样执行工具
 */
public class ReactiveSampler {
    private static final AtomicLong counter = new AtomicLong(0);

    /**
     * 按计数抽样执行副作用：每 rate 次执行一次
     */
    public static <T> Consumer<T> byCount(long rate, Consumer<T> consumer) {
        return t -> {
            if (counter.incrementAndGet() % rate == 0) {
                consumer.accept(t);
            }
        };
    }

    /**
     * 按概率抽样执行副作用：probability 是 0~1 的 double 值
     */
    public static <T> Consumer<T> byProbability(double probability, Consumer<T> consumer) {
        return t -> {
            if (Math.random() < probability) {
                consumer.accept(t);
            }
        };
    }

    /**
     * 任意策略抽样执行副作用（开发环境全执行）
     */
    public static <T> Consumer<T> sampled(Predicate<T> shouldRun, Consumer<T> consumer) {
        return t -> {
            if ( shouldRun.test(t)) {
                consumer.accept(t);
            }
        };
    }

}
