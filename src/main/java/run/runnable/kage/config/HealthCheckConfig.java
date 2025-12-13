package run.runnable.kage.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Slf4j
@Component
@Order(1) // 最先执行
@RequiredArgsConstructor
public class HealthCheckConfig implements ApplicationRunner {

    private final R2dbcEntityTemplate r2dbcEntityTemplate;
    private final ReactiveStringRedisTemplate redisTemplate;

    @Override
    public void run(ApplicationArguments args) {
        checkPostgreSQL();
        checkRedis();
        log.info("✅ 数据库连接检查通过");
    }

    private void checkPostgreSQL() {
        try {
            r2dbcEntityTemplate.getDatabaseClient()
                    .sql("SELECT 1")
                    .fetch()
                    .first()
                    .block(Duration.ofSeconds(10));
            log.info("PostgreSQL 连接成功");
        } catch (Exception e) {
            log.error("❌ PostgreSQL 连接失败: {}", e.getMessage());
            throw new RuntimeException("PostgreSQL 连接失败，应用无法启动", e);
        }
    }

    private void checkRedis() {
        try {
            redisTemplate.opsForValue()
                    .set("health:check", "ok", Duration.ofSeconds(10))
                    .block(Duration.ofSeconds(10));
            log.info("Redis 连接成功");
        } catch (Exception e) {
            log.error("❌ Redis 连接失败: {}", e.getMessage());
            throw new RuntimeException("Redis 连接失败，应用无法启动", e);
        }
    }
}
