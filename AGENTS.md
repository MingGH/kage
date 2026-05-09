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

- Java 17
- Reactive stack: WebFlux + R2DBC + Reactive Redis
- Discord integration: JDA 5.x
- Test framework: JUnit 5 (Jupiter)
- Build tool: Maven (wrapper included: `./mvnw`)
- Package: `run.runnable.kage`

## Deployment

- **CI/CD**: GitHub Actions (`.github/workflows/deploy.yml`) triggered on push to `main`
- **Runtime**: K3s cluster (deployment config: `k3s-deployment-prod.yaml`)
- **Namespace**: `996ninja`
- **Docker registry**: Ali Container Registry (registry.cn-hongkong.aliyuncs.com/runnable-run/kage)
