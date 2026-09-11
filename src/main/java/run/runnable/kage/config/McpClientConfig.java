package run.runnable.kage.config;

import io.modelcontextprotocol.client.McpAsyncClient;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.time.Duration;

/**
 * 自定义 MCP Client 配置，添加 Authorization header 支持
 * 初始化（initialize）统一在 DeepSeekService 中按 client 容错执行，避免单服务故障阻断启动
 */
@Slf4j
@Configuration
public class McpClientConfig {

    @Value("${jina.api-key:}")
    private String jinaApiKey;

    @Bean
    @Primary
    @ConditionalOnProperty(name = "jina.mcp.enabled", havingValue = "true", matchIfMissing = true)
    public McpAsyncClient jinaAsyncMcpClient() {
        log.info("创建自定义 Jina MCP Client，API Key: {}...",
                jinaApiKey.length() > 10 ? jinaApiKey.substring(0, 10) : "未配置");

        // 创建带 Authorization header 的请求构建器
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .header("Authorization", "Bearer " + jinaApiKey);

        // 使用 Streamable HTTP 传输 (Jina MCP 2025-03-26 spec)
        HttpClientStreamableHttpTransport transport = HttpClientStreamableHttpTransport.builder("https://mcp.jina.ai")
                .clientBuilder(HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(30)))
                .requestBuilder(requestBuilder)
                .endpoint("/v1")
                .build();

        // 创建异步 MCP Client
        McpAsyncClient client = McpClient.async(transport)
                .clientInfo(new McpSchema.Implementation("kage-bot", "1.0.0"))
                .requestTimeout(Duration.ofSeconds(120))
                .build();

        log.info("Jina MCP Client 创建完成");
        return client;
    }

    /**
     * 996Ninja 摸鱼 MCP 服务（fish-ninja，Streamable HTTP，无鉴权）
     */
    @Bean
    @ConditionalOnProperty(name = "fish-ninja.mcp.enabled", havingValue = "true", matchIfMissing = true)
    public McpAsyncClient fishNinjaAsyncMcpClient(
            @Value("${fish-ninja.mcp.base-url:https://fish.mcp.996.ninja}") String baseUrl,
            @Value("${fish-ninja.mcp.endpoint:/mcp}") String endpoint) {
        HttpClientStreamableHttpTransport transport = HttpClientStreamableHttpTransport.builder(baseUrl)
                .clientBuilder(HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(30)))
                .endpoint(endpoint)
                .build();

        McpAsyncClient client = McpClient.async(transport)
                .clientInfo(new McpSchema.Implementation("kage-bot-fishninja", "1.0.0"))
                .requestTimeout(Duration.ofSeconds(60))
                .build();

        log.info("FishNinja MCP Client 创建完成: {}{}", baseUrl, endpoint);
        return client;
    }
}
