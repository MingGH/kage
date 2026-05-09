package run.runnable.kage.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DeepSeek API 调用测试
 * 直接使用 HTTP 客户端测试，不依赖 Spring 上下文
 */
class DeepSeekApiTest {

    private String apiKey;
    private HttpClient httpClient;

    @BeforeEach
    void setUp() {
        apiKey = System.getenv("DEEPSEEK_API_KEY");
        httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
    }

    @Test
    void testSimpleChat() throws Exception {
        if (apiKey == null || apiKey.isEmpty()) {
            System.out.println("跳过测试：未设置 DEEPSEEK_API_KEY 环境变量");
            return;
        }

        String requestBody = """
                {
                  "model": "deepseek-v4-flash",
                  "messages": [
                    {"role": "user", "content": "说一个字"}
                  ]
                }
                """;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.deepseek.com/chat/completions"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        System.out.println("状态码: " + response.statusCode());
        System.out.println("响应: " + response.body());

        assertEquals(200, response.statusCode(), "API 调用应该成功");
    }

    @Test
    void testChatWithHistoryHavingEmptyReasoningContent() throws Exception {
        // 测试：历史消息有空的 reasoning_content
        if (apiKey == null || apiKey.isEmpty()) {
            System.out.println("跳过测试：未设置 DEEPSEEK_API_KEY 环境变量");
            return;
        }

        String requestBody = """
                {
                  "model": "deepseek-v4-flash",
                  "messages": [
                    {"role": "user", "content": "什么是北京时间？"},
                    {"role": "assistant", "content": "北京时间是中国的标准时间。", "reasoning_content": ""},
                    {"role": "user", "content": "继续"}
                  ]
                }
                """;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.deepseek.com/chat/completions"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        System.out.println("状态码: " + response.statusCode());
        System.out.println("响应: " + response.body());

        // 如果返回 400，说明空字符串不行
        if (response.statusCode() == 400) {
            System.err.println("空 reasoning_content 导致 400 错误！");
        }
        
        assertEquals(200, response.statusCode(), "API 调用应该成功");
    }

    @Test
    void testChatWithHistoryWithoutReasoningContent() throws Exception {
        // 测试：历史消息没有 reasoning_content 字段
        if (apiKey == null || apiKey.isEmpty()) {
            System.out.println("跳过测试：未设置 DEEPSEEK_API_KEY 环境变量");
            return;
        }

        String requestBody = """
                {
                  "model": "deepseek-v4-flash",
                  "messages": [
                    {"role": "user", "content": "什么是北京时间？"},
                    {"role": "assistant", "content": "北京时间是中国的标准时间。"},
                    {"role": "user", "content": "继续"}
                  ]
                }
                """;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.deepseek.com/chat/completions"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        System.out.println("状态码: " + response.statusCode());
        System.out.println("响应: " + response.body());

        assertEquals(200, response.statusCode(), "API 调用应该成功");
    }

    @Test
    void testChatWithHistoryHavingReasoningContent() throws Exception {
        // 测试：历史消息有实际的 reasoning_content
        if (apiKey == null || apiKey.isEmpty()) {
            System.out.println("跳过测试：未设置 DEEPSEEK_API_KEY 环境变量");
            return;
        }

        String requestBody = """
                {
                  "model": "deepseek-v4-flash",
                  "messages": [
                    {"role": "user", "content": "什么是北京时间？"},
                    {"role": "assistant", "content": "北京时间是中国的标准时间。", "reasoning_content": "用户询问北京时间，我需要解释。"},
                    {"role": "user", "content": "继续"}
                  ]
                }
                """;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.deepseek.com/chat/completions"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        System.out.println("状态码: " + response.statusCode());
        System.out.println("响应: " + response.body());

        assertEquals(200, response.statusCode(), "API 调用应该成功");
    }

    @Test
    void testChatWithToolCallInHistory() throws Exception {
        // 测试：历史消息包含 tool_call 时，必须传 reasoning_content
        // 这是 DeepSeek 的特殊要求
        if (apiKey == null || apiKey.isEmpty()) {
            System.out.println("跳过测试：未设置 DEEPSEEK_API_KEY 环境变量");
            return;
        }

        // 模拟之前有 tool call 的历史消息，但没有 reasoning_content
        String requestBody = """
                {
                  "model": "deepseek-v4-flash",
                  "messages": [
                    {"role": "user", "content": "现在几点？"},
                    {"role": "assistant", "content": "", "tool_calls": [{"id": "call_123", "type": "function", "function": {"name": "getCurrentTime", "arguments": "{}"}}]},
                    {"role": "tool", "content": "2026-05-09 15:00:00", "tool_call_id": "call_123"},
                    {"role": "user", "content": "谢谢"}
                  ]
                }
                """;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.deepseek.com/chat/completions"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        System.out.println("状态码: " + response.statusCode());
        System.out.println("响应: " + response.body());

        // 这个测试可能会返回 400，因为 tool_call 后必须传 reasoning_content
        if (response.statusCode() == 400) {
            System.err.println("包含 tool_call 的历史消息需要 reasoning_content！");
        }
        
        // 我们接受这个测试可能失败，只是为了确认问题
    }

    @Test
    void testNonThinkingMode() throws Exception {
        // 测试：使用 deepseek-chat（非 thinking 模式）
        if (apiKey == null || apiKey.isEmpty()) {
            System.out.println("跳过测试：未设置 DEEPSEEK_API_KEY 环境变量");
            return;
        }

        String requestBody = """
                {
                  "model": "deepseek-chat",
                  "messages": [
                    {"role": "user", "content": "说一个字"}
                  ]
                }
                """;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.deepseek.com/chat/completions"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        System.out.println("状态码: " + response.statusCode());
        System.out.println("响应: " + response.body());

        assertEquals(200, response.statusCode(), "API 调用应该成功");
    }
}
