package run.runnable.kage.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.test.web.reactive.server.WebTestClient;

@WebFluxTest(controllers = AppHealthController.class, properties = "project.version=1.0.0")
class AppHealthControllerTest {

    @Autowired
    private WebTestClient webClient;

    @Test
    @DisplayName("健康检查接口测试")
    void hlCheck_shouldReturnSuccess() {
        webClient.get().uri("/hl/check")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo(200)
                .jsonPath("$.message").isEqualTo("Success")
                .jsonPath("$.data").value(v -> v.toString().contains("1.0.0"));
    }
}
