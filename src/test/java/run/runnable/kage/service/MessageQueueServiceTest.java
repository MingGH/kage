package run.runnable.kage.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ReactiveListOperations;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import reactor.core.publisher.Mono;
import run.runnable.kage.domain.UserMessage;
import run.runnable.kage.repository.UserMessageRepository;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MessageQueueServiceTest {

    @Mock
    private ReactiveStringRedisTemplate redisTemplate;
    
    @Mock
    private ReactiveListOperations<String, String> listOperations;
    
    @Mock
    private UserMessageRepository userMessageRepository;

    private MessageQueueService service;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForList()).thenReturn(listOperations);
        service = new MessageQueueService(redisTemplate, userMessageRepository);
    }

    @Test
    @DisplayName("推送消息到队列")
    void pushMessage_shouldPushToRedis() {
        when(listOperations.rightPush(anyString(), anyString())).thenReturn(Mono.just(1L));
        
        UserMessage message = UserMessage.builder()
                .messageId("123")
                .content("test")
                .build();
        
        service.pushMessage(message);
        
        verify(listOperations).rightPush(eq("discord:message:queue"), anyString());
    }
}
