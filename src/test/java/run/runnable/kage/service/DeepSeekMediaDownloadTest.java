package run.runnable.kage.service;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.content.Media;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import run.runnable.kage.repository.ChatMessageRepository;
import run.runnable.kage.service.tool.ChannelHistoryTool;
import run.runnable.kage.service.tool.CurrentTimeTool;
import run.runnable.kage.service.tool.LeaderboardTool;
import run.runnable.kage.service.tool.RagSearchTool;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 测试 DeepSeekService 的图片下载与 Media 构建逻辑。
 * 核心验证：本服务先把 Discord 图片下载成字节，再以 base64 提供给模型，
 * 而不是直接把 URL 透传给 DeepSeek 服务端下载。
 */
@ExtendWith(MockitoExtension.class)
class DeepSeekMediaDownloadTest {

    private static final byte[] JPEG_BYTES = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00, 0x11, 0x22};

    private static HttpServer server;

    // 记录每次请求收到的 User-Agent 头
    private static final Map<String, String> REQUEST_USER_AGENTS = new ConcurrentHashMap<>();

    @Mock
    private ChatClient.Builder chatClientBuilder;
    @Mock
    private ChatMessageRepository chatMessageRepository;
    @Mock
    private ChannelHistoryTool channelHistoryTool;
    @Mock
    private CurrentTimeTool currentTimeTool;
    @Mock
    private LeaderboardTool leaderboardTool;
    @Mock
    private RagSearchTool ragSearchTool;
    @Mock
    private ReactiveStringRedisTemplate redisTemplate;

    private DeepSeekService service;

    @BeforeAll
    static void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/image.jpg", exchange -> {
            REQUEST_USER_AGENTS.put("/image.jpg", exchange.getRequestHeaders().getFirst("User-Agent"));
            byte[] body = JPEG_BYTES;
            exchange.getResponseHeaders().add("Content-Type", "image/jpeg");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
    }

    @AfterAll
    static void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @BeforeEach
    void setUp() {
        when(chatClientBuilder.defaultToolCallbacks(any(org.springframework.ai.tool.ToolCallback[].class)))
                .thenReturn(chatClientBuilder);
        when(chatClientBuilder.defaultTools(any(), any(), any(), any()))
                .thenReturn(chatClientBuilder);
        when(chatClientBuilder.build()).thenReturn(mock(ChatClient.class));

        service = new DeepSeekService(
                chatClientBuilder,
                chatMessageRepository,
                List.of(),
                channelHistoryTool,
                currentTimeTool,
                leaderboardTool,
                ragSearchTool,
                redisTemplate,
                WebClient.builder(),
                "test-system-prompt"
        );
    }

    @Test
    @DisplayName("resolveImageMime 应剥掉 URL query 再判断扩展名")
    void resolveImageMime_stripsQueryString() {
        String url = "https://cdn.discordapp.com/attachments/123/456/IMG_20260915_180846_907.jpg?ex=6aab51a5&is=6aaa0025&hm=abc";
        MimeType mime = ReflectionTestUtils.invokeMethod(service, "resolveImageMime", url);
        assertEquals(MimeTypeUtils.IMAGE_JPEG, mime, "带 query 的 .jpg URL 应识别为 jpeg");
    }

    @Test
    @DisplayName("downloadImage 应带 User-Agent 下载字节并构建 base64 Media")
    void downloadImage_shouldDownloadBytesWithUserAgent() {
        int port = server.getAddress().getPort();
        String url = "http://127.0.0.1:" + port + "/image.jpg";

        @SuppressWarnings("unchecked")
        Mono<Media> mediaMono = (Mono<Media>) ReflectionTestUtils.invokeMethod(service, "downloadImage", url);
        Media media = mediaMono.block();

        assertEquals(MimeTypeUtils.IMAGE_JPEG, media.getMimeType());
        assertInstanceOf(byte[].class, media.getData(), "data 应为字节数组（base64 内联），而非 URL");
        assertArrayEquals(JPEG_BYTES, media.getDataAsByteArray());
        assertEquals("Mozilla/5.0 (compatible; KageBot/1.0)",
                REQUEST_USER_AGENTS.get("/image.jpg"), "下载请求必须携带 User-Agent");
    }

    @Test
    @DisplayName("buildMedia 应下载多张图并跳过失败项")
    void buildMedia_shouldDownloadAndSkipFailures() {
        int port = server.getAddress().getPort();
        List<String> urls = List.of(
                "http://127.0.0.1:" + port + "/image.jpg",
                "http://127.0.0.1:" + port + "/not-exist.jpg"
        );

        @SuppressWarnings("unchecked")
        Mono<List<Media>> mediaMono = (Mono<List<Media>>) ReflectionTestUtils.invokeMethod(service, "buildMedia", urls);
        List<Media> mediaList = mediaMono.block();

        assertEquals(1, mediaList.size(), "失败的那张应被跳过，只保留成功下载的一张");
        assertArrayEquals(JPEG_BYTES, mediaList.get(0).getDataAsByteArray());
    }
}