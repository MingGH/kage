package run.runnable.kage.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import run.runnable.kage.dto.ArkImageResponse;
import run.runnable.kage.dto.GeneratedImage;

import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.Set;

/**
 * 豆包 Seedream 图片生成服务（火山方舟 Ark API）
 */
@Slf4j
@Service
public class SeedreamService {

    private static final String GENERATE_PATH = "/api/plan/v3/images/generations";
    // 2K 图生成实测约 30s，超时上限放宽
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(150);
    // 下载 2K PNG 通常几秒即可
    private static final Duration DOWNLOAD_TIMEOUT = Duration.ofSeconds(30);
    private static final Set<String> ALLOWED_SIZES = Set.of("1K", "2K");

    private final WebClient webClient;
    private final String apiKey;
    private final String model;
    private final String defaultSize;

    public SeedreamService(WebClient.Builder webClientBuilder,
                           @Value("${ark.api-key:}") String apiKey,
                           @Value("${ark.base-url:https://ark.cn-beijing.volces.com}") String baseUrl,
                           @Value("${ark.model:doubao-seedream-5.0-lite}") String model,
                           @Value("${ark.size:2K}") String defaultSize) {
        this.apiKey = apiKey;
        this.model = model;
        this.defaultSize = defaultSize;
        this.webClient = webClientBuilder.baseUrl(baseUrl).build();
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("ARK_API_KEY 未配置，图片生成功能将不可用");
        }
    }

    /**
     * 生成图片，返回签名 URL（24 小时有效）
     *
     * @param prompt 图片描述，不能为空
     * @param size   尺寸（1K/2K），空则用默认值
     */
    public Mono<GeneratedImage> generateImage(String prompt, String size) {
        if (apiKey == null || apiKey.isBlank()) {
            return Mono.error(new IllegalStateException("画图功能未配置 ARK_API_KEY"));
        }
        String actualSize = normalizeSize(size);
        if (actualSize == null) {
            return Mono.error(new IllegalArgumentException("不支持的尺寸: " + size + "（可选 1K/2K）"));
        }
        Map<String, Object> body = Map.of(
                "model", model,
                "prompt", prompt,
                "size", actualSize,
                "output_format", "png",
                "watermark", false
        );

        long start = System.currentTimeMillis();
        return webClient.post()
                .uri(GENERATE_PATH)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(ArkImageResponse.class)
                .timeout(REQUEST_TIMEOUT)
                .map(this::extractFirst)
                .map(item -> new GeneratedImage(item.url(), model, actualSize))
                .doOnSuccess(img -> log.info("图片生成成功: size={}, 耗时={}ms", actualSize,
                        System.currentTimeMillis() - start))
                .onErrorMap(e -> e instanceof WebClientResponseException
                        || e instanceof IllegalStateException
                        || e instanceof IllegalArgumentException ? e
                        : new IllegalStateException("图片生成失败: " + e.getMessage(), e));
    }

    /**
     * 下载生成的图片（Ark 签名 URL），返回 PNG 字节
     */
    public Mono<byte[]> downloadImage(String url) {
        return webClient.get()
                .uri(URI.create(url))
                .retrieve()
                .bodyToMono(byte[].class)
                .timeout(DOWNLOAD_TIMEOUT);
    }

    /**
     * 尺寸归一化（大小写不敏感），非法返回 null
     */
    private String normalizeSize(String size) {
        String actualSize = (size == null || size.isBlank()) ? defaultSize : size.trim().toUpperCase();
        return ALLOWED_SIZES.contains(actualSize) || actualSize.equals(defaultSize) ? actualSize : null;
    }

    private ArkImageResponse.Item extractFirst(ArkImageResponse response) {
        if (response == null || response.data() == null || response.data().isEmpty()
                || response.data().get(0).url() == null || response.data().get(0).url().isBlank()) {
            throw new IllegalStateException("Ark 响应中没有图片 URL");
        }
        return response.data().get(0);
    }
}
