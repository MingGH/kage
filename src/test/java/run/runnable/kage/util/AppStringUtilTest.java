package run.runnable.kage.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppStringUtilTest {

    @Test
    @DisplayName("Unicode转中文测试")
    void convertUnicodeToChinese_shouldConvertCorrectly() {
        String input = "\\u4f60\\u597d";
        assertEquals("你好", AppStringUtil.convertUnicodeToChinese(input));
        
        String mixed = "Hello \\u4f60\\u597d World";
        assertEquals("Hello 你好 World", AppStringUtil.convertUnicodeToChinese(mixed));
        
        String noUnicode = "Hello World";
        assertEquals("Hello World", AppStringUtil.convertUnicodeToChinese(noUnicode));

        // Malformed or incomplete unicode
        String malformed = "\\u123";
        assertEquals("123", AppStringUtil.convertUnicodeToChinese(malformed));
    }

    @Test
    @DisplayName("字符串连接测试")
    void joinSep_shouldJoinCorrectly() {
        assertEquals("a,b,c", AppStringUtil.joinSep(",", "a", "b", "c"));
        assertEquals("a-b", AppStringUtil.joinSep("-", "a", "b"));
        assertEquals("single", AppStringUtil.joinSep(",", "single"));
        assertEquals("", AppStringUtil.joinSep(","));
    }

    @Test
    @DisplayName("提取书名测试")
    void extractBookTitles_shouldExtractCorrectly() {
        String input = "推荐几本书：《Java编程思想》、《Clean Code》和《重构》。";
        List<String> titles = AppStringUtil.extractBookTitles(input);
        
        assertEquals(3, titles.size());
        assertEquals("Java编程思想", titles.get(0));
        assertEquals("Clean Code", titles.get(1));
        assertEquals("重构", titles.get(2));
        
        String noBooks = "这里没有书名。";
        assertTrue(AppStringUtil.extractBookTitles(noBooks).isEmpty());
        
        String emptyBook = "空书名《》测试";
        List<String> emptyTitles = AppStringUtil.extractBookTitles(emptyBook);
        assertEquals(1, emptyTitles.size());
        assertEquals("", emptyTitles.get(0));
    }
}
