package run.runnable.kage.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import io.netty.resolver.NoopAddressResolverGroup;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;
import reactor.netty.transport.ProxyProvider;
import run.runnable.kage.constants.AppConstant;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Configuration
public class AppConfig {



    @Bean("webClient")
    @Profile(AppConstant.ACTIVE_ENV_PROD)
    public WebClient webClientProdProd() {
        ConnectionProvider provider = ConnectionProvider
                .builder("webClient")
                .maxConnections(1000) // 设置最大连接数
                .pendingAcquireTimeout(Duration.ofSeconds(60)) // 等待连接的最大时间
                .build();

        // 配置 HttpClient
        HttpClient httpClient = HttpClient.create(provider)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 1000 * 60 * 3)
                .responseTimeout(Duration.ofMinutes(3))
                .doOnConnected(conn ->
                        conn.addHandlerLast(new ReadTimeoutHandler(60))
                                .addHandlerLast(new WriteTimeoutHandler(60))
                );
        return WebClient.builder()
                .codecs(codecs -> codecs.defaultCodecs().maxInMemorySize(50 * 1024 * 1024))
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    @Bean("webClient")
    @Profile(AppConstant.ACTIVE_ENV_DEV)
    public WebClient webClientProdDev() {
        ConnectionProvider provider = ConnectionProvider
                .builder("webClient")
                .maxConnections(1000) // 设置最大连接数
                .pendingAcquireTimeout(Duration.ofSeconds(60)) // 等待连接的最大时间
                .build();

        // 配置 HttpClient
        HttpClient httpClient = HttpClient.create(provider)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 1000 * 60 * 3)
                .responseTimeout(Duration.ofMinutes(3))
                .proxy(proxy -> proxy.type(ProxyProvider.Proxy.HTTP)
                        .address(new InetSocketAddress("127.0.0.1", 7890)))
                .doOnConnected(conn ->
                        conn.addHandlerLast(new ReadTimeoutHandler(60))
                                .addHandlerLast(new WriteTimeoutHandler(60))
                );
        return WebClient.builder()
                .codecs(codecs -> codecs.defaultCodecs().maxInMemorySize(50 * 1024 * 1024))
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .defaultHeader("Content-Type", "application/json")
                .build();
    }


    public static WebClient create(String proxyHost, int proxyPort, String username, String password) {
        HttpClient httpClient = HttpClient.create()
                .resolver(NoopAddressResolverGroup.INSTANCE)
                .proxy(proxy -> proxy
                        .type(ProxyProvider.Proxy.SOCKS5)
                        .address(new InetSocketAddress(proxyHost, proxyPort))
                        .username(username)
                        .password(s -> password)
                )
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 1000 * 60 * 3)
                .responseTimeout(Duration.ofMinutes(3))
                .doOnConnected(conn ->
                        conn.addHandlerLast(new ReadTimeoutHandler(60))
                                .addHandlerLast(new WriteTimeoutHandler(60))
                )
                .doOnConnected(conn -> conn
                        .addHandlerLast(new ReadTimeoutHandler(10, TimeUnit.SECONDS))
                        .addHandlerLast(new WriteTimeoutHandler(10, TimeUnit.SECONDS))
                );

        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
//                .defaultHeader(HttpHeaders.USER_AGENT, randomUserAgent())
//                .defaultHeader(HttpHeaders.ACCEPT, "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
//                .defaultHeader(HttpHeaders.ACCEPT_LANGUAGE, "en-US,en;q=0.9")
//                .defaultHeader("Sec-Fetch-Site", "none")
//                .defaultHeader("Sec-Fetch-Mode", "navigate")
//                .defaultHeader("Sec-Fetch-User", "?1")
//                .defaultHeader("Sec-Fetch-Dest", "document")
//                .defaultHeader(HttpHeaders.CONNECTION, "keep-alive")
                .codecs(config -> config.defaultCodecs().maxInMemorySize(20 * 1024 * 1024))
                .build();
    }



}
