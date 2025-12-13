package run.runnable.kage.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

@Slf4j
public class ReactorUtil {


    /**
     * 为Flux流添加执行时间统计功能
     *
     * @param flux       需要统计执行时间的Flux流
     * @param onComplete 执行完成后的回调函数，参数为执行耗时(毫秒)
     * @param <T>        Flux流中元素的类型
     * @return 包装后的Flux流，在完成时会调用onComplete回调并记录执行时间
     */
    public static <T> Flux<T> timeFlux(Flux<T> flux, Consumer<Long> onComplete) {
        return Flux.defer(() -> {
            long startTime = System.currentTimeMillis();
            return flux.doFinally(signal -> {
                long elapsed = System.currentTimeMillis() - startTime;
                onComplete.accept(elapsed);
            });
        });
    }

    public static <T> Mono<T> timeMono(Mono<T> mono, Consumer<Long> onComplete) {
        return Mono.defer(() -> {
            long startTime = System.currentTimeMillis();
            return mono.doFinally(signal -> {
                long elapsed = System.currentTimeMillis() - startTime;
                onComplete.accept(elapsed);
            });
        });
    }



    public static Mono<?> getRandomElement(Flux<?> flux) {
        return flux.count() // Get the total count of elements
                .flatMap(count -> {
                    if (count == 0) {
                        return Mono.empty(); // Return empty if there are no elements
                    }
                    int randomIndex = ThreadLocalRandom.current().nextInt(count.intValue());
                    return flux.skip(randomIndex).next(); // Skip randomIndex elements and take the next one
                });
    }

    /**
     * 检查链接是否有效
     *
     * @param url 要检查的链接
     * @return Mono<Boolean> 异步返回是否有效
     */
    public static Mono<Boolean> isLinkValid(WebClient webClient, String url) {
        return webClient.head()
                .uri(url) // 使用 HEAD 方法避免下载内容
                .retrieve()
                .toBodilessEntity() // 不需要响应体
                .map(response -> {
                    int statusCode = response.getStatusCode().value();
                    return statusCode >= 200 && statusCode < 400; // HTTP 2xx 和 3xx 表示链接有效
                })
                .onErrorReturn(false); // 如果发生异常，认为链接无效
    }

    /**
     * 从远程链接直接下载文件并返回 InputStream
     *
     * @param fileUrl 文件的 URL
     * @return Mono<InputStream> 异步返回文件内容流
     */
    public static Mono<InputStream> downloadFileAsInputStream(WebClient webClient, String fileUrl) {
        return webClient.get()
                .uri(fileUrl)
                .header(HttpHeaders.USER_AGENT, "WebFlux File Downloader")
                .retrieve()
                .bodyToMono(byte[].class) // 下载文件内容为字节数组
                .map(ByteArrayInputStream::new); // 将字节数组转换为 InputStream
    }
}
