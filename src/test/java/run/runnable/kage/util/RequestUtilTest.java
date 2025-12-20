package run.runnable.kage.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;

import java.net.InetSocketAddress;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RequestUtilTest {

    @Test
    @DisplayName("获取客户端IP - X-Forwarded-For 存在")
    void getClientIp_withHeader() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/")
                .header("X-Forwarded-For", "192.168.1.1, 10.0.0.1")
                .build();
        
        assertEquals("192.168.1.1", RequestUtil.getClientIp(request));
    }

    @Test
    @DisplayName("获取客户端IP - X-Forwarded-For 不存在")
    void getClientIp_withoutHeader() {
        InetSocketAddress address = new InetSocketAddress("127.0.0.1", 8080);
        MockServerHttpRequest request = MockServerHttpRequest.get("/")
                .remoteAddress(address)
                .build();
        
        assertEquals("127.0.0.1", RequestUtil.getClientIp(request));
    }
}
