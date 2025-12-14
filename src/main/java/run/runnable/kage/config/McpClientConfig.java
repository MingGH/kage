package run.runnable.kage.config;

import io.modelcontextprotocol.client.McpAsyncClient;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.transport.WebClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

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

        // 创建带 Authorization header 的 WebClient Builder
        WebClient.Builder webClientBuilder = WebClient.builder()
                .baseUrl("https://mcp.jina.ai")
                .filter(addAuthorizationHeader());

        // 使用 Streamable HTTP 传输 (Jina MCP 2025-03-26 spec)
        WebClientStreamableHttpTransport transport = WebClientStreamableHttpTransport.builder(webClientBuilder)
                .endpoint("/v1")
                .build();

        // 创建异步 MCP Client
        McpAsyncClient client = McpClient.async(transport)
                .clientInfo(new McpSchema.Implementation("kage-bot", "1.0.0"))
                .requestTimeout(Duration.ofSeconds(30))
                .build();

        // 初始化连接
        client.initialize().block(Duration.ofSeconds(30));
        log.info("Jina MCP Client 初始化完成");

        return client;
    }

    /**
     * 创建一个 ExchangeFilterFunction，为每个请求添加 Authorization header
     */
    private ExchangeFilterFunction addAuthorizationHeader() {
        return ExchangeFilterFunction.ofRequestProcessor(clientRequest -> {
            log.debug("MCP 请求: {} {}", clientRequest.method(), clientRequest.url());
            
            // 如果请求中没有 Authorization header，添加它
            if (!clientRequest.headers().containsKey("Authorization")) {
                log.debug("添加 Authorization header");
                ClientRequest newRequest = ClientRequest.from(clientRequest)
                        .header("Authorization", "Bearer " + jinaApiKey)
                        .build();
                return Mono.just(newRequest);
            }
            return Mono.just(clientRequest);
        });
    }
}
