package run.runnable.kage.service.tool;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.List;

/**
 * AI 工具：搜索 996.ninja 网站知识库（Cloudflare Vectorize RAG）
 */
@Slf4j
@Component
public class RagSearchTool {

    private static final String EMBEDDING_MODEL = "@cf/baai/bge-m3";
    private static final String VECTORIZE_INDEX = "ai-search-crimson-art-08f7";
    private static final double SCORE_THRESHOLD = 0.5;
    private static final int TOP_K = 5;
    private static final String ACCOUNT_ID = "8034b6f645143efa728dad5bdf39e7bd";
    private static final String API_TOKEN = "BLlP4_mjYsoGOeBhoqjMro12UXYFQnGqhBufAkQL";

    private final WebClient webClient;

    public RagSearchTool(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.build();
    }

    @Tool(description = "搜索996忍者网站知识库，获取摸鱼技巧、网站功能、忍术教程等相关内容。当用户问关于996忍者网站的内容、摸鱼技巧、网站有什么功能、推荐摸鱼方法等问题时使用此工具。")
    public String ragSearch(@ToolParam(description = "用户的搜索问题") String query) {
        log.info("RAG 搜索: {}", query);

        try {
            // 1. 生成 embedding
            List<Double> vector = generateEmbedding(query);
            if (vector == null || vector.isEmpty()) {
                return "知识库搜索失败：无法生成向量";
            }

            // 2. 查询 Vectorize
            List<VectorizeMatch> matches = queryVectorize(vector);

            // 3. 过滤低分结果
            List<VectorizeMatch> filtered = matches.stream()
                    .filter(m -> m.score >= SCORE_THRESHOLD)
                    .toList();

            if (filtered.isEmpty()) {
                return "未找到相关内容";
            }

            // 4. 格式化返回
            StringBuilder sb = new StringBuilder();
            sb.append("找到 ").append(filtered.size()).append(" 条相关内容：\n\n");
            for (int i = 0; i < filtered.size(); i++) {
                VectorizeMatch m = filtered.get(i);
                sb.append(i + 1).append(". ").append(m.title).append("\n");
                sb.append("   ").append(m.description).append("\n");
                sb.append("   链接: ").append(m.url).append("\n\n");
            }
            sb.append("请基于以上内容回答用户问题，并推荐相关页面链接。");

            log.info("RAG 搜索完成，返回 {} 条结果", filtered.size());
            return sb.toString();

        } catch (Exception e) {
            log.error("RAG 搜索失败: {}", e.getMessage());
            return "知识库搜索服务暂时不可用";
        }
    }

    private List<Double> generateEmbedding(String text) {
        String url = String.format("https://api.cloudflare.com/client/v4/accounts/%s/ai/run/%s", ACCOUNT_ID, EMBEDDING_MODEL);

        org.json.JSONObject body = new org.json.JSONObject();
        body.put("text", new org.json.JSONArray().put(text));

        String response = webClient.post()
                .uri(url)
                .header("Authorization", "Bearer " + API_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body.toString())
                .retrieve()
                .bodyToMono(String.class)
                .block();

        JSONObject resp = JSON.parseObject(response);
        JSONArray data = resp.getJSONObject("result").getJSONArray("data").getJSONArray(0);
        List<Double> vector = new ArrayList<>(data.size());
        for (int i = 0; i < data.size(); i++) {
            vector.add(data.getDouble(i));
        }
        return vector;
    }

    private List<VectorizeMatch> queryVectorize(List<Double> vector) {
        String url = String.format("https://api.cloudflare.com/client/v4/accounts/%s/vectorize/v2/indexes/%s/query", ACCOUNT_ID, VECTORIZE_INDEX);

        org.json.JSONObject body = new org.json.JSONObject();
        body.put("vector", new org.json.JSONArray(vector));
        body.put("topK", TOP_K);
        body.put("returnMetadata", "all");
        body.put("returnValues", false);

        String response = webClient.post()
                .uri(url)
                .header("Authorization", "Bearer " + API_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body.toString())
                .retrieve()
                .bodyToMono(String.class)
                .block();

        JSONObject resp = JSON.parseObject(response);
        JSONArray matches = resp.getJSONObject("result").getJSONArray("matches");
        List<VectorizeMatch> results = new ArrayList<>();
        if (matches != null) {
            for (int i = 0; i < matches.size(); i++) {
                JSONObject match = matches.getJSONObject(i);
                JSONObject metadata = match.getJSONObject("metadata");
                JSONObject file = metadata != null ? metadata.getJSONObject("file") : null;
                results.add(new VectorizeMatch(
                        match.getDoubleValue("score"),
                        file != null ? file.getString("title") : "",
                        file != null ? file.getString("description") : "",
                        metadata != null ? metadata.getString("key") : ""
                ));
            }
        }
        return results;
    }

    private record VectorizeMatch(double score, String title, String description, String url) {}
}
