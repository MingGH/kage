package run.runnable.kage.command;

import net.dv8tion.jda.api.entities.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import run.runnable.kage.service.DeepSeekService;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 测试 CommandManager 提取图片附件：当前消息 + 引用消息里的图片都要能被识别。
 */
@ExtendWith(MockitoExtension.class)
class CommandManagerImageExtractTest {

    @Mock
    private CommandRegistry commandRegistry;
    @Mock
    private ReactiveStringRedisTemplate redisTemplate;
    @Mock
    private DeepSeekService deepSeekService;

    private CommandManager commandManager;

    @BeforeEach
    void setUp() {
        commandManager = new CommandManager(commandRegistry, redisTemplate, deepSeekService);
    }

    private Message.Attachment attachment(String contentType, String url) {
        return new Message.Attachment(0, url, url, "img", contentType, null, 0, 0, 0, false, null, 0, null);
    }

    @Test
    @DisplayName("当前消息的图片附件应被提取")
    void extractImageUrls_fromCurrentMessage() {
        Message message = mock(Message.class);
        when(message.getAttachments()).thenReturn(List.of(
                attachment("image/png", "https://cdn.discordapp.com/current.png"),
                attachment("application/pdf", "https://cdn.discordapp.com/file.pdf")
        ));
        when(message.getReferencedMessage()).thenReturn(null);

        @SuppressWarnings("unchecked")
        List<String> urls = (List<String>) ReflectionTestUtils.invokeMethod(commandManager, "extractImageUrls", message);

        assertEquals(List.of("https://cdn.discordapp.com/current.png"), urls,
                "应只提取 image/* 附件，忽略 pdf");
    }

    @Test
    @DisplayName("引用消息里的图片附件也应被提取")
    void extractImageUrls_includeReferencedMessageImages() {
        Message message = mock(Message.class);
        when(message.getAttachments()).thenReturn(List.of());

        Message referenced = mock(Message.class);
        when(referenced.getAttachments()).thenReturn(List.of(
                attachment("image/jpeg", "https://cdn.discordapp.com/quoted.jpg")
        ));
        when(message.getReferencedMessage()).thenReturn(referenced);

        @SuppressWarnings("unchecked")
        List<String> urls = (List<String>) ReflectionTestUtils.invokeMethod(commandManager, "extractImageUrls", message);

        assertIterableEquals(List.of("https://cdn.discordapp.com/quoted.jpg"), urls,
                "引用消息里的图片必须一起识别");
    }

    @Test
    @DisplayName("当前消息与引用消息的图片应合并，且当前消息在前")
    void extractImageUrls_mergeCurrentAndReferenced() {
        Message message = mock(Message.class);
        when(message.getAttachments()).thenReturn(List.of(
                attachment("image/png", "https://cdn.discordapp.com/current.png")
        ));

        Message referenced = mock(Message.class);
        when(referenced.getAttachments()).thenReturn(List.of(
                attachment("image/jpeg", "https://cdn.discordapp.com/quoted.jpg")
        ));
        when(message.getReferencedMessage()).thenReturn(referenced);

        @SuppressWarnings("unchecked")
        List<String> urls = (List<String>) ReflectionTestUtils.invokeMethod(commandManager, "extractImageUrls", message);

        assertEquals(List.of("https://cdn.discordapp.com/current.png", "https://cdn.discordapp.com/quoted.jpg"), urls);
    }
}