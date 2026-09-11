package run.runnable.kage.dto;

/**
 * 图片生成请求（HTTP 接口入参）
 *
 * @param prompt 图片描述
 * @param size   尺寸，可选 1K / 2K，默认 2K
 */
public record ImageGenerationRequest(String prompt, String size) {
}
