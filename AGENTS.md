# AGENTS.md

## Project Overview

Kage (布布管家) is a Discord bot built with Spring Boot WebFlux, R2DBC PostgreSQL, Redis, and Spring AI.

## Build Commands

```bash
# Compile
mvn compile

# Compile tests
mvn test-compile

# Run all tests
mvn test

# Run specific test
mvn test -Dtest=ClassName

# Build (skip tests)
mvn package -DskipTests

# Build + Docker image
mvn clean package dockerfile:build -DskipTests
```

## Important Notes

### Spring Boot 4.0.6 Breaking Changes

- **`@WebFluxTest` removed**: The `org.springframework.boot.test.autoconfigure.web.reactive` package (including `@WebFluxTest` and `@AutoConfigureWebTestClient`) is gone from `spring-boot-test-autoconfigure`. Use `WebTestClient.bindToController()` instead.
- **`WebTestClient` NOT auto-configured**: In SB4, `WebTestClient` is not auto-configured even with `@SpringBootTest`. Manual creation required via `bindToServer()` or `bindToController()`.

### Spring AI 2.0.0-M6

- Uses `spring.ai.openai.*` properties with `base-url` pointing to DeepSeek (OpenAI-compatible API)
- Model: `deepseek-v4-flash` with thinking mode explicitly disabled via `extra-body`
- **DO NOT use `spring-ai-starter-model-deepseek`** — it has bugs: `DeepSeekChatModel.createRequest()` doesn't serialize `reasoningContent`, and `DeepSeekChatOptions` lacks a `thinking` toggle. Use `spring-ai-starter-model-openai` instead.
- Thinking mode is disabled because Spring AI cannot pass `reasoning_content` back during internal tool-call loops, causing 400 errors

### Project Conventions

- Java 25（JDK 23+ 需 `<maven.compiler.proc>full</maven.compiler.proc>` 才能启用 Lombok 注解处理）
- Reactive stack: WebFlux + R2DBC + Reactive Redis
- Discord integration: JDA 5.x
- Test framework: JUnit 5 (Jupiter)
- Build tool: Maven (wrapper included: `./mvnw`)
- Package: `run.runnable.kage`

### Image Generation (/draw)

- **SeedreamService** (`service/SeedreamService.java`): 火山方舟 Ark API 客户端（`doubao-seedream-5.0-lite`），生成 + 下载图片字节，全响应式 WebClient；`output_format` 只能 `png`/`jpeg`，返回 `data[0].url` 签名 URL 24h 有效
- **DrawRateLimiter** (`service/DrawRateLimiter.java`): Redis 日配额，全局 100 张/天 + 单用户 30 张/天（Asia/Shanghai 日切，key 带 TTL 2 天）；INCR 占位→超限 DECR 归还；`tryAcquire` 用 `Mono.defer` 惰性组装全局检查（用户配额没过不碰全局）
- **DrawCommand** (`command/impl/DrawCommand.java`): `/draw prompt size?`，deferReply 占位 → 图片附件回复（`MessageEditAction.setFiles`，JDA 编辑消息追加附件只有 `setFiles` 没有 `addFile`）；下载失败降级文本+URL，附件上传失败走 `ReplyHook` 的 `onFailure` 回调降级
- **ImageController** (`/image/generate`): 临时测试接口，`@ConditionalOnProperty(ark.test-endpoint.enabled)` 默认关闭，开启需在 deployment 加 env `ARK_TEST_ENDPOINT_ENABLED=true`（仅全局限流，无用户维度）
- **ARK_API_KEY**: K8s Secret `kage-secret` 注入，yaml 中只有 `${ARK_API_KEY:}` 占位符，禁止明文入库

## Deployment

- **CI/CD**: GitHub Actions (`.github/workflows/deploy.yml`) triggered on push to `main`
- **Runtime**: K3s cluster (deployment config: `k3s-deployment-prod.yaml`)
- **Namespace**: `996ninja`
- **Docker registry**: Ali Container Registry (registry.cn-hongkong.aliyuncs.com/runnable-run/kage)
