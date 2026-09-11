package run.runnable.kage.dto;

/**
 * 图片生成结果
 *
 * @param url   签名 URL（24 小时有效）
 * @param model 使用的模型
 * @param size  图片尺寸
 */
public record GeneratedImage(String url, String model, String size) {
}
