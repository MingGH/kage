package run.runnable.kage.service.tool;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * AI 工具：获取当前时间
 */
@Slf4j
@Component
public class CurrentTimeTool {

    private static final String DEFAULT_TIMEZONE = "Asia/Shanghai";

    @Tool(description = "获取当前时间。当用户询问'现在几点'、'今天是几号'、'现在是什么时间'等问题时使用此工具。")
    public String getCurrentTime(
            @ToolParam(description = "时区，默认 Asia/Shanghai，可选值如 UTC、America/New_York 等") String timezone
    ) {
        String tz = (timezone == null || timezone.isBlank()) ? DEFAULT_TIMEZONE : timezone;
        
        try {
            ZoneId zoneId = ZoneId.of(tz);
            LocalDateTime now = LocalDateTime.now(zoneId);
            
            String formatted = now.format(DateTimeFormatter.ofPattern("yyyy年M月d日 EEEE HH:mm:ss", Locale.CHINESE));
            log.info("获取当前时间: timezone={}, time={}", tz, formatted);
            
            return String.format("当前时间（%s）：%s", tz, formatted);
        } catch (Exception e) {
            log.warn("无效的时区: {}", tz);
            // 回退到默认时区
            LocalDateTime now = LocalDateTime.now(ZoneId.of(DEFAULT_TIMEZONE));
            String formatted = now.format(DateTimeFormatter.ofPattern("yyyy年M月d日 EEEE HH:mm:ss", Locale.CHINESE));
            return String.format("当前时间（%s）：%s（注：指定的时区 %s 无效，已使用默认时区）", DEFAULT_TIMEZONE, formatted, tz);
        }
    }
}
