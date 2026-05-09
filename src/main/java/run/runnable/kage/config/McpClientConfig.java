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
 */
@Slf4j
@Configuration
public class McpClientConfig {

    @Value("${jina.api-key:}")
    private String jinaApiKey;

    @Value("${jina.mcp.enabled:true}")
    private boolean mcpEnabled;

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

        // 初始化连接
        client.initialize().block(Duration.ofSeconds(30));
        log.info("Jina MCP Client 初始化完成");

        return client;
    }
}
