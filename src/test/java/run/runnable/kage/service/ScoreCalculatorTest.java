package run.runnable.kage.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * ScoreCalculator 单元测试
 * 覆盖所有积分计算规则
 */
class ScoreCalculatorTest {

    private ScoreCalculator scoreCalculator;

    @BeforeEach
    void setUp() {
        scoreCalculator = new ScoreCalculator();
    }

    // ========== 基础积分测试 (长度 <= 20) ==========

    @Test
    @DisplayName("空字符串应返回基础积分 1 分")
    void emptyString_shouldReturn1() {
        assertEquals(1, scoreCalculator.calculateScore(""));
    }

    @Test
    @DisplayName("null 应返回基础积分 1 分")
    void nullContent_shouldReturn1() {
        assertEquals(1, scoreCalculator.calculateScore(null));
    }

    @Test
    @DisplayName("短消息 (<=20字符) 应返回基础积分 1 分")
    void shortMessage_shouldReturn1() {
        assertEquals(1, scoreCalculator.calculateScore("Hello World"));
        assertEquals(1, scoreCalculator.calculateScore("12345678901234567890")); // 正好 20 字符
    }

    // ========== 长消息奖励测试 (长度 > 20 且 <= 50) ==========

    @Test
    @DisplayName("中等消息 (>20 且 <=50字符) 应返回 2 分")
    void mediumMessage_shouldReturn2() {
        // 21 字符
        assertEquals(2, scoreCalculator.calculateScore("123456789012345678901"));
        // 50 字符
        assertEquals(2, scoreCalculator.calculateScore("12345678901234567890123456789012345678901234567890"));
    }

    // ========== 较长消息奖励测试 (长度 > 50 且 <= 100) ==========

    @Test
    @DisplayName("较长消息 (>50 且 <=100字符) 应返回 3 分")
    void longMessage_shouldReturn3() {
        // 51 字符
        String msg51 = "a".repeat(51);
        assertEquals(3, scoreCalculator.calculateScore(msg51));
        // 100 字符
        String msg100 = "a".repeat(100);
        assertEquals(3, scoreCalculator.calculateScore(msg100));
    }

    // ========== 超长消息奖励测试 (长度 > 100) ==========

    @Test
    @DisplayName("超长消息 (>100字符) 应返回 4 分")
    void veryLongMessage_shouldReturn4() {
        // 101 字符
        String msg101 = "a".repeat(101);
        assertEquals(4, scoreCalculator.calculateScore(msg101));
        // 200 字符
        String msg200 = "a".repeat(200);
        assertEquals(4, scoreCalculator.calculateScore(msg200));
    }

    // ========== 空白字符处理测试 ==========

    @Test
    @DisplayName("纯空白字符应返回基础积分 1 分")
    void whitespaceOnly_shouldReturn1() {
        assertEquals(1, scoreCalculator.calculateScore("   "));
        assertEquals(1, scoreCalculator.calculateScore("\t\t\t"));
        assertEquals(1, scoreCalculator.calculateScore("\n\n\n"));
        assertEquals(1, scoreCalculator.calculateScore("  \t  \n  "));
    }

    @Test
    @DisplayName("包含空白字符的消息应忽略空白计算长度")
    void messageWithWhitespace_shouldIgnoreWhitespace() {
        // "Hello World" 去除空格后是 "HelloWorld" = 10 字符，应返回 1 分
        assertEquals(1, scoreCalculator.calculateScore("Hello World"));
        
        // 21 个非空白字符 + 空格，应返回 2 分
        assertEquals(2, scoreCalculator.calculateScore("a b c d e f g h i j k l m n o p q r s t u"));
        
        // 51 个非空白字符 + 空格，应返回 3 分
        String msgWith51Chars = "a ".repeat(51).trim(); // 51 个 'a' 和空格
        assertEquals(3, scoreCalculator.calculateScore(msgWith51Chars));
    }

    // ========== 边界值测试 ==========

    @Test
    @DisplayName("边界值 20 字符应返回 1 分")
    void boundary20_shouldReturn1() {
        String msg20 = "a".repeat(20);
        assertEquals(1, scoreCalculator.calculateScore(msg20));
    }

    @Test
    @DisplayName("边界值 21 字符应返回 2 分")
    void boundary21_shouldReturn2() {
        String msg21 = "a".repeat(21);
        assertEquals(2, scoreCalculator.calculateScore(msg21));
    }

    @Test
    @DisplayName("边界值 50 字符应返回 2 分")
    void boundary50_shouldReturn2() {
        String msg50 = "a".repeat(50);
        assertEquals(2, scoreCalculator.calculateScore(msg50));
    }

    @Test
    @DisplayName("边界值 51 字符应返回 3 分")
    void boundary51_shouldReturn3() {
        String msg51 = "a".repeat(51);
        assertEquals(3, scoreCalculator.calculateScore(msg51));
    }

    @Test
    @DisplayName("边界值 100 字符应返回 3 分")
    void boundary100_shouldReturn3() {
        String msg100 = "a".repeat(100);
        assertEquals(3, scoreCalculator.calculateScore(msg100));
    }

    @Test
    @DisplayName("边界值 101 字符应返回 4 分")
    void boundary101_shouldReturn4() {
        String msg101 = "a".repeat(101);
        assertEquals(4, scoreCalculator.calculateScore(msg101));
    }
}
