package run.runnable.kage.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import run.runnable.kage.common.ApiResponse;
import run.runnable.kage.constants.AppConstant;
import run.runnable.kage.dto.GeneratedImage;
import run.runnable.kage.dto.ImageGenerationRequest;
import run.runnable.kage.service.DrawRateLimiter;
import run.runnable.kage.service.SeedreamService;

import java.util.HashMap;
import java.util.Map;

/**
 * 图片生成测试接口，公网暴露风险高，默认关闭（ark.test-endpoint.enabled=true 开启）
 */
@Slf4j
@RestController
@RequestMapping("/image")
@ConditionalOnProperty(name = "ark.test-endpoint.enabled", havingValue = "true")
@RequiredArgsConstructor
public class ImageController {

    private static final int MAX_PROMPT_LENGTH = AppConstant.MAX_PROMPT_LENGTH;

    private final SeedreamService seedreamService;
    private final DrawRateLimiter drawRateLimiter;

    /**
     * 图片生成（豆包 Seedream，测试用）
     * POST /image/generate {"prompt": "一只忍者在摸鱼", "size": "2K"}
     */
    @PostMapping("/generate")
    public Mono<ApiResponse<Map<String, Object>>> generate(@RequestBody ImageGenerationRequest request) {
        if (request == null || request.prompt() == null || request.prompt().isBlank()) {
            return Mono.just(ApiResponse.error(400, "prompt 不能为空"));
        }
        String prompt = request.prompt().trim();
        if (prompt.length() > MAX_PROMPT_LENGTH) {
            return Mono.just(ApiResponse.error(400, "prompt 过长，最多 " + MAX_PROMPT_LENGTH + " 字符"));
        }

        return drawRateLimiter.tryAcquireGlobal()
                .then(Mono.defer(() -> seedreamService.generateImage(prompt, request.size()).map(this::toResponse)))
                .onErrorResume(this::toErrorResponse);
    }

    private ApiResponse<Map<String, Object>> toResponse(GeneratedImage image) {
        Map<String, Object> data = new HashMap<>();
        data.put("url", image.url());
        data.put("model", image.model());
        data.put("size", image.size());
        data.put("note", "签名 URL 24 小时内有效");
        return ApiResponse.success(data);
    }

    private Mono<ApiResponse<Map<String, Object>>> toErrorResponse(Throwable e) {
        if (e instanceof DrawRateLimiter.QuotaExceededException qe) {
            return Mono.just(ApiResponse.error(429, qe.getMessage()));
        }
        if (e instanceof IllegalArgumentException iae) {
            return Mono.just(ApiResponse.error(400, iae.getMessage()));
        }
        if (e instanceof WebClientResponseException wcre) {
            String body = wcre.getResponseBodyAsString();
            log.error("图片生成失败: status={}, responseBody={}", wcre.getStatusCode().value(), body, wcre);
            String message = body != null && body.length() > 300 ? body.substring(0, 300) + "..." : body;
            return Mono.just(ApiResponse.error(wcre.getStatusCode().value(), "图片生成失败: " + message));
        }
        String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
        log.error("图片生成失败: {}", msg, e);
        return Mono.just(ApiResponse.error(500, "图片生成失败: " + msg));
    }
}
