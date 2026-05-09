package run.runnable.kage.controller;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.reactive.server.WebTestClient;

class AppHealthControllerTest {

    @Test
    void testHealthCheck() {
        AppHealthController controller = new AppHealthController();
        ReflectionTestUtils.setField(controller, "version", "0.0.1-SNAPSHOT");

        WebTestClient.bindToController(controller)
                .build()
                .get()
                .uri("/hl/check")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo(200)
                .jsonPath("$.message").isEqualTo("Success")
                .jsonPath("$.data").value(data -> {
                    assert data instanceof String;
                    assert ((String) data).contains("I am fine");
                });
    }
}
