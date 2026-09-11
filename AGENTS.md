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
- Model: `deepseek-v4-flash-vision-exp`（vision 多模态：@提及带图片附件时经 `Media`/`UserMessage.builder()` 传入，最多 3 张，历史消息不回放图片）
- **DO NOT use `spring-ai-starter-model-deepseek`** — it has bugs: `DeepSeekChatModel.createRequest()` doesn't serialize `reasoningContent`, and `DeepSeekChatOptions` lacks a `thinking` toggle. Use `spring-ai-starter-model-openai` instead.
- Thinking mode is disabled because Spring AI cannot pass `reasoning_content` back during internal tool-call loops, causing 400 errors
- **Reactor 语义坑**：`Mono<Void>.subscribe(值消费者)` 永不触发（空完成只发 complete 信号），完成逻辑必须用三参 subscribe 的 onComplete

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

### MCP 集成 (Jina + fish-ninja)

- **多客户端聚合**：`DeepSeekService` 注入 `List<McpAsyncClient>`（`config/McpClientConfig.java` 按服务各建一个 bean），每个 client 独立 `initialize` + `listTools`（20s 超时、try-catch 降级），**单 MCP 服务故障只 warn 跳过，不阻断 bot 启动**；新增 MCP 服务 = 加一个 bean + application.yaml 配置，无需改其他代码
- **工具名前缀是正常行为**：Jina 与 fish-ninja（CF agents SDK）都会按 `clientInfo.name` 给工具加前缀（`kage-bot`→`k_b_`、`kage-bot-fishninja`→`k_b_f_`），代码里的 `k_b_` 剥离只影响系统提示词展示，AI 调用仍用带前缀的全名——不要"修复"这个前缀
- **fish-ninja**：996Ninja 摸鱼 MCP 服务（塔罗/金价/假期/热榜等 9 工具），无状态 Streamable HTTP，无鉴权；本地工具与它重复时以 MCP 为准删除本地版（如 TarotTool 已删，塔罗走 MCP `draw_tarot`）

### 内部测试服务（生产集成测试入口）

- `config/InternalTestServer.java`：仅绑定 `127.0.0.1:8081`（集群外不可达），由 env `INTERNAL_TEST_ENABLED=true` 开启，**用 `kubectl exec <pod> -- curl http://127.0.0.1:8081/...` 调用**——这是不开 Discord 就能做生产集成测试的入口，测试 AI/工具/画图链路优先走它
- `GET /internal/tools`：已加载的 MCP 工具清单（验证 MCP 连接）
- `POST /internal/chat`：`{"guildId","userId","message"}`，走完整 AI + 工具链路（验证工具真实调用）
- `POST /internal/draw`：`{"userId","prompt","size"}`，限流→生成→下载全链路（验证画图，不含 Discord 回复段）
- **坑：不要用 SmartLifecycle + `@EventListener(ApplicationReadyEvent)` 双重触发**——本服务曾因 `start()` 既是接口实现又是事件监听导致同 JVM bind 两次、`Address already in use` 崩溃循环；事件监听 + `@PreDestroy` 清理即可

### JDA 响应模式的坑（防静默失败）

- **Reactor 语义坑（/draw 静默卡死的真根因）**：`Mono<Void>.subscribe(valueConsumer, errorConsumer)` 的 **valueConsumer 永远不会被调用**（空完成只触发 complete 信号）——完成后的逻辑必须用三参 `subscribe(null, onError, onComplete)` 的第三参接收。诊断特征：订阅副作用（如 Redis INCR）全部执行、成功回调却一次不跑
- **交互占位回复统一用 `event.getHook()`**，不要直接用 `deferReply().queue(hook -> hook.sendMessage(...))` 的回调参数（该模式曾导致 /draw 全链路静默卡死：无占位消息、无日志、无超时）
- **所有 JDA `queue()` 必须挂显式失败回调**（`queue(success, err -> log.error(...))`），JDA 默认失败日志不可依赖；"命令执行了但 Discord/日志毫无动静"优先怀疑静默失败
- 排查静默卡死的思路：用下游副作用（如 Redis 计数 key 的创建时间，`TTL` 反推）确定卡点分层——限流器完成但无后续日志 = 卡在 JDA 交互层

### 生产排查环境（需要用户提供）

- **kubectl 集群访问**（查 Pod 日志/exec/滚动状态），Redis 与各 Secret 连接信息在 `kage-secret` 中（含 `REDIS_DATABASE`，注意 redis-cli 需 `-n <db>`，默认 db 0 会误判为空）
- 运行时为 Azul Zulu（HotSpot）JDK 25：排查线程问题用 `kubectl exec <pod> -- jcmd 1 Thread.print`（已从 OpenJ9 换到 Zulu，OpenJ9 无 jcmd/jstack 曾导致只能 kill -3）；镜像内有 curl，可直接 exec 调内部测试服务
- **JDA 交互层（斜杠命令/按钮）无法用内部接口模拟**，只能请用户在 Discord 实际操作验证；其余功能均可通过内部测试服务覆盖

## Deployment

- **CI/CD**: GitHub Actions (`.github/workflows/deploy.yml`) triggered on push to `main`
- **Runtime**: K3s cluster (deployment config: `k3s-deployment-prod.yaml`)
- **Namespace**: `996ninja`
- **Docker registry**: Ali Container Registry (registry.cn-hongkong.aliyuncs.com/runnable-run/kage)
