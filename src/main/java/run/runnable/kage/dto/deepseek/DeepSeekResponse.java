package run.runnable.kage.dto.deepseek;

import lombok.Data;

import java.util.List;

@Data
public class DeepSeekResponse {
    private String id;
    private String object;
    private Long created;
    private String model;
    private List<Choice> choices;

    @Data
    public static class Choice {
        private Integer index;
        private Message message;
        private String finishReason;
    }

    @Data
    public static class Message {
        private String role;
        private String content;
    }
}
