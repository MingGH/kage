package run.runnable.kage.util;

import org.springframework.http.server.reactive.ServerHttpRequest;

public interface RequestUtil {

    /**
     * 获取客户端真实IP地址
     * 优先从X-Forwarded-For头部获取，如果没有则从远程地址获取
     *
     * @param request HTTP请求对象
     * @return 客户端IP地址
     */
    public static String getClientIp(ServerHttpRequest request) {
        return request.getHeaders().getFirst("X-Forwarded-For") != null
                ? request.getHeaders().getFirst("X-Forwarded-For").split(",")[0].trim()
                : request.getRemoteAddress().getAddress().getHostAddress();
    }


}


