package run.runnable.kage.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ReactorHttpHandlerAdapter;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.netty.http.server.HttpServer;
import run.runnable.kage.dto.GeneratedImage;
import run.runnable.kage.service.DeepSeekService;
import run.runnable.kage.service.DrawRateLimiter;
import run.runnable.kage.service.SeedreamService;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.springframework.web.reactive.function.server.RequestPredicates.GET;
import static org.springframework.web.reactive.function.server.RequestPredicates.POST;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;

/**
 * Pod 内部集成测试服务：仅绑定 127.0.0.1，集群外无法访问
 * 通过 kubectl exec + curl 调用，用于生产环境功能验证（chat/draw/工具清单）
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "internal-test.enabled", havingValue = "true")
@RequiredArgsConstructor
public class InternalTestServer implements SmartLifecycle {

    private static final int PORT = 8081;

    private final DeepSeekService deepSeekService;
    private final SeedreamService seedreamService;
    private final DrawRateLimiter drawRateLimiter;

    private volatile Disposable serverDisposable;

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        RouterFunction<ServerResponse> routes = buildRoutes();
        serverDisposable = HttpServer.create()
                .host("127.0.0.1")
                .port(PORT)
                .handle(new ReactorHttpHandlerAdapter(RouterFunctions.toHttpHandler(routes)))
                .bindNow(Duration.ofSeconds(10));
        log.info("内部测试服务已启动: http://127.0.0.1:{} (仅限 Pod 内访问)", PORT);
    }

    @Override
    public void stop() {
        if (serverDisposable != null) {
            serverDisposable.dispose();
        }
    }

    @Override
    public boolean isRunning() {
        return serverDisposable != null && !serverDisposable.isDisposed();
    }

    private RouterFunction<ServerResponse> buildRoutes() {
        return route(GET("/internal/tools"), this::handleTools)
                .andRoute(POST("/internal/chat"), this::handleChat)
                .andRoute(POST("/internal/draw"), this::handleDraw);
    }

    /**
     * GET /internal/tools：已加载的 MCP 工具清单
     */
    private Mono<ServerResponse> handleTools(ServerRequest request) {
        Map<String, Object> data = new HashMap<>();
        data.put("count", deepSeekService.getLoadedToolNames().size());
        data.put("tools", deepSeekService.getLoadedToolNames());
        return okJson(data);
    }

    /**
     * POST /internal/chat：模拟用户提问，走完整 AI + 工具链路
     * body: {"guildId": "...", "userId": "...", "message": "..."}
     */
    private Mono<ServerResponse> handleChat(ServerRequest request) {
        return readBody(request).flatMap(body -> {
            String message = str(body, "message");
            if (isBlank(message)) {
                return badRequest("message 必填");
            }
            String guildId = str(body, "guildId");
            String userId = str(body, "userId");
            log.info("[internal-test] chat: user={}, message={}", userId, abbreviate(message));

            Mono<String> reply = deepSeekService.chat(guildId, userId, message)
                    .timeout(Duration.ofSeconds(120));
            return reply.flatMap(r -> okJson(Map.of("reply", r)));
        });
    }

    /**
     * POST /internal/draw：走限流 + 生成 + 下载的完整链路（不含 Discord 回复）
     * body: {"userId": "...", "prompt": "...", "size": "2K"}
     */
    private Mono<ServerResponse> handleDraw(ServerRequest request) {
        return readBody(request).flatMap(body -> {
            String userId = str(body, "userId");
            String prompt = str(body, "prompt");
            if (isBlank(prompt) || isBlank(userId)) {
                return badRequest("userId 和 prompt 必填");
            }
            log.info("[internal-test] draw: user={}, size={}", userId, str(body, "size"));

            Mono<GeneratedImage> generated = drawRateLimiter.tryAcquire(userId)
                    .then(Mono.defer(() -> seedreamService.generateImage(prompt, str(body, "size"))));
            Mono<Map<String, Object>> result = generated
                    .flatMap(this::downloadAndWrap)
                    .timeout(Duration.ofSeconds(180))
                    .onErrorResume(e -> Mono.just(errorBody(e)));
            return result.flatMap(this::okJson);
        });
    }

    /**
     * 生成后尝试下载字节，返回统一结构（下载失败仍返回 URL）
     */
    private Mono<Map<String, Object>> downloadAndWrap(GeneratedImage image) {
        return seedreamService.downloadImage(image.url())
                .map(bytes -> {
                    Map<String, Object> data = new HashMap<>();
                    data.put("url", image.url());
                    data.put("size", image.size());
                    data.put("bytes", bytes.length);
                    data.put("attachmentReady", true);
                    return data;
                })
                .onErrorReturn(urlOnlyBody(image));
    }

    private Map<String, Object> urlOnlyBody(GeneratedImage image) {
        Map<String, Object> data = new HashMap<>();
        data.put("url", image.url());
        data.put("size", image.size());
        data.put("attachmentReady", false);
        data.put("note", "下载失败，仅返回 URL");
        return data;
    }

    private Map<String, Object> errorBody(Throwable e) {
        Map<String, Object> data = new HashMap<>();
        data.put("error", messageOf(e));
        return data;
    }

    @SuppressWarnings("unchecked")
    private Mono<Map<String, Object>> readBody(ServerRequest request) {
        return request.bodyToMono(Map.class)
                .map(body -> (Map<String, Object>) body)
                .defaultIfEmpty(Map.of());
    }

    private Mono<ServerResponse> okJson(Object body) {
        return ServerResponse.ok().bodyValue(body);
    }

    private Mono<ServerResponse> badRequest(String message) {
        return ServerResponse.status(HttpStatus.BAD_REQUEST).bodyValue(Map.of("error", message));
    }

    private String str(Map<String, Object> body, String key) {
        Object value = body.get(key);
        return value == null ? null : value.toString();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String messageOf(Throwable e) {
        return e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
    }

    private String abbreviate(String value) {
        return value.length() > 100 ? value.substring(0, 100) + "..." : value;
    }
}
