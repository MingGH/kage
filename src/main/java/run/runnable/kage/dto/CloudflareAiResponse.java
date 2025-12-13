package run.runnable.kage.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Cloudflare AI (Workers AI) 通用响应 DTO
 * 适配形如：
 * {
 *   "result": {
 *     "response": "...",
 *     "usage": {
 *       "prompt_tokens": 26,
 *       "completion_tokens": 256,
 *       "total_tokens": 282
 *     }
 *   },
 *   "success": true,
 *   "errors": [],
 *   "messages": []
 * }
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CloudflareAiResponse {

    private Result result;
    private boolean success;
    private List<CloudflareError> errors;
    private List<CloudflareMessage> messages;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Result {
        private String response;
        private Usage usage;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Usage {
        @JsonProperty("prompt_tokens")
        private Integer promptTokens;
        @JsonProperty("completion_tokens")
        private Integer completionTokens;
        @JsonProperty("total_tokens")
        private Integer totalTokens;
    }

    /**
     * Cloudflare V4 通用错误结构通常包含 code 与 message
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CloudflareError {
        private Integer code;
        private String message;
    }

    /**
     * Cloudflare V4 通用消息结构（与 errors 结构相同），用于额外提示
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CloudflareMessage {
        private Integer code;
        private String message;
    }
}