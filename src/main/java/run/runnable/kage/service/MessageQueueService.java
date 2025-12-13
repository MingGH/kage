package run.runnable.kage.service;

import com.alibaba.fastjson2.JSON;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import run.runnable.kage.domain.UserMessage;
import run.runnable.kage.repository.UserMessageRepository;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
@RequiredArgsConstructor
public class MessageQueueService {

    private static final String QUEUE_KEY = "discord:message:queue";

    private final ReactiveStringRedisTemplate redisTemplate;
    private final UserMessageRepository userMessageRepository;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean running = new AtomicBoolean(true);

    @PostConstruct
    public void startConsumer() {
        executor.submit(this::consumeMessages);
        log.info("消息队列消费者已启动");
    }

    @PreDestroy
    public void stopConsumer() {
        running.set(false);
        executor.shutdown();
        log.info("消息队列消费者已停止");
    }

    /**
     * 推送消息到队列
     */
    public void pushMessage(UserMessage message) {
        String json = JSON.toJSONString(message);
        redisTemplate.opsForList()
                .rightPush(QUEUE_KEY, json)
                .subscribe(
                        v -> log.debug("消息已入队: {}", message.getMessageId()),
                        e -> log.error("消息入队失败: {}", e.getMessage())
                );
    }

    /**
     * 单线程消费消息
     */
    private void consumeMessages() {
        int errorCount = 0;
        while (running.get()) {
            try {
                // 阻塞式获取消息，超时 1 秒
                redisTemplate.opsForList()
                        .leftPop(QUEUE_KEY, Duration.ofSeconds(1))
                        .flatMap(json -> {
                            if (json != null) {
                                UserMessage message = JSON.parseObject(json, UserMessage.class);
                                return userMessageRepository.save(message);
                            }
                            return reactor.core.publisher.Mono.empty();
                        })
                        .block(Duration.ofSeconds(5));
                errorCount = 0; // 成功后重置错误计数
            } catch (Exception e) {
                if (running.get()) {
                    errorCount++;
                    // 连续错误时降低日志频率，并增加等待时间
                    if (errorCount <= 3 || errorCount % 60 == 0) {
                        log.error("消费消息异常 (第{}次): {}", errorCount, e.getMessage());
                    }
                    try {
                        // 出错后等待一段时间再重试，避免疯狂重试
                        Thread.sleep(Math.min(errorCount * 1000L, 30000L));
                    } catch (InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }
    }
}
