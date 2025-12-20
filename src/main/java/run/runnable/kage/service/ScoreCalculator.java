package run.runnable.kage.service;

import org.springframework.stereotype.Component;

/**
 * 摸鱼积分计算器
 * 根据消息内容长度计算摸鱼积分
 */
@Component
public class ScoreCalculator {

    /**
     * 计算单条消息的摸鱼积分
     * 
     * 积分规则：
     * - 基础积分：1 分
     * - 长度 > 20 字符：2 分
     * - 长度 > 50 字符：3 分
     * - 长度 > 100 字符：4 分
     * 
     * @param content 消息内容
     * @return 积分值 (1-4分)
     */
    public int calculateScore(String content) {
        if (content == null) {
            return 1;
        }
        
        // 去除空白字符后计算长度
        int length = content.replaceAll("\\s+", "").length();
        
        if (length > 100) {
            return 4;
        } else if (length > 50) {
            return 3;
        } else if (length > 20) {
            return 2;
        }
        return 1;
    }
}
