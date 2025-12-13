package run.runnable.kage.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import run.runnable.kage.common.ApiResponse;

/**
 * 健康检查
 *
 * @author asher
 * @date 2025/07/20
 */
@RestController
@RequestMapping("hl")
public class AppHealthController {

    @Value("${project.version}")
    private String version;


    @GetMapping("check")
    public Mono<ApiResponse<String>> hlCheck(){
        return Mono.just(ApiResponse.success("I am fine. version is: " + version));
    }


}
