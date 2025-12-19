package run.runnable.kage.service.tool;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * AI 工具：塔罗牌占卜
 */
@Slf4j
@Component
public class TarotTool {

    private static final String TAROT_API_URL = "https://api.996.ninja/tarot/horoscope";
    
    private final WebClient webClient;

    public TarotTool() {
        this.webClient = WebClient.builder()
                .baseUrl(TAROT_API_URL)
                .build();
    }
 
    @Tool(description = "进行塔罗牌占卜。当用户想要占卜、抽塔罗牌、算命、看运势时使用此工具。返回塔罗牌名称，AI需要根据牌面含义进行解读。")
    public String drawTarotCards(
            @ToolParam(description = "抽取的塔罗牌数量，默认1张，最少1张，最多3张") Integer count
    ) {
        int n = (count == null || count < 1) ? 1 : Math.min(count, 3);
        
        log.info("开始塔罗牌占卜，抽取 {} 张牌", n);
        
        try {
            String response = webClient.get()
                    .uri(uriBuilder -> uriBuilder.queryParam("n", n).build())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            
            if (response == null) {
                return "塔罗牌占卜服务暂时不可用，请稍后再试";
            }
            
            JSONObject json = JSON.parseObject(response);
            if (json.getInteger("status") != 200) {
                return "塔罗牌占卜失败：" + json.getString("message");
            }
            
            JSONArray cards = json.getJSONArray("data");
            StringBuilder result = new StringBuilder();
            result.append("🔮 塔罗牌占卜结果（共 ").append(cards.size()).append(" 张牌）：\n\n");
            
            for (int i = 0; i < cards.size(); i++) {
                JSONObject card = cards.getJSONObject(i);
                String name = card.getString("name");
                result.append("第 ").append(i + 1).append(" 张牌：").append(name).append("\n");
            }
            
            result.append("\n请根据以上塔罗牌为用户进行解读。");
            
            log.info("塔罗牌占卜完成，抽取了 {} 张牌", cards.size());
            return result.toString();
            
        } catch (Exception e) {
            log.error("塔罗牌占卜失败: {}", e.getMessage());
            return "塔罗牌占卜服务暂时不可用，请稍后再试";
        }
    }
}
