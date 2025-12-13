package run.runnable.kage.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import run.runnable.kage.dto.CloudflareAiResponse;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
public class CloudflareService {



    @Value("${cloudflare.apiToken}")
    private String apiToken;
    @Value("${cloudflare.accountId}")
    private String accountId;
    @Value("${cloudflare.ai.model:@cf/meta/llama-3.2-3b-instruct}")
    private String aiModel;

    @Autowired
    private WebClient webClient;

    @Value("classpath:template/cloudflare/graphql.json")
    private Resource requestBodyTemplateResource;

    @Value("classpath:template/cloudflare/graphql-by-days.json")
    private Resource requestBodyTemplateByDaysResource;




    /**
     * 调用 Cloudflare AI（Workers AI）聊天接口
     * 对应 curl：
     * POST https://api.cloudflare.com/client/v4/accounts/{accountId}/ai/run/{model}
     * Header: Authorization: Bearer {apiToken}
     * Body: { "messages": [{"role":"system","content":"..."},{"role":"user","content":"..."}] }
     *
     * @param messages 消息列表（role: system/user/assistant, content: 文本）
     * @return AI 响应 JSON
     */
    public Mono<CloudflareAiResponse> runAiChat(List<Message> messages){
        JSONObject req = new JSONObject();
        JSONArray msgs = new JSONArray();
        for (Message m : messages){
            JSONObject jsonObject = new JSONObject().fluentPut("role", m.role()).fluentPut("content", m.content());
            msgs.add(jsonObject);
        }
        req.put("messages", msgs);

        String url = String.format("https://api.cloudflare.com/client/v4/accounts/%s/ai/run/%s", accountId, aiModel);
        log.info("Cloudflare AI request url:{}, model:{}", url, aiModel);

        return webClient.post()
                .uri(url)
                .header("Authorization", "Bearer " + apiToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(req.toString())
                .retrieve()
                .bodyToMono(String.class)
                .map(it -> JSON.parseObject(it, CloudflareAiResponse.class));
    }

    /**
     * 简单封装，便于直接使用 system + user 两段消息
     */
    public Mono<CloudflareAiResponse> runAiChat(String systemPrompt, String userContent){
        return runAiChat(
                List.of(
                    new Message("system", systemPrompt),
                    new Message("user", userContent)
                )
        );
    }

    /**
     * 消息数据结构
     */
    public record Message(String role, String content) {}
}
