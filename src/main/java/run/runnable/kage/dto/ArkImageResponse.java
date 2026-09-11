package run.runnable.kage.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * 豆包 Seedream（Ark API）图片生成响应
 * 文档约定：data[0].url 为签名 URL，24 小时有效
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ArkImageResponse(String model, Long created, List<Item> data) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(String url, String size) {
    }
}
