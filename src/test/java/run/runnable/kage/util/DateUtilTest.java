package run.runnable.kage.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DateUtilTest {

    @Test
    @DisplayName("日期格式化测试")
    void dateFormat_shouldFormatCorrectly() {
        LocalDateTime dateTime = LocalDateTime.of(2023, 10, 1, 12, 30, 45);
        String pattern = "yyyy-MM-dd HH:mm:ss";
        String expected = "2023-10-01 12:30:45";
        
        assertEquals(expected, DateUtil.dateFormat(dateTime, pattern));
        
        String pattern2 = "yyyy/MM/dd";
        String expected2 = "2023/10/01";
        assertEquals(expected2, DateUtil.dateFormat(dateTime, pattern2));
    }
}
