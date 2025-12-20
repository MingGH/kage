package run.runnable.kage.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UserStats {
    
    private String userId;
    private String userName;
    
    // 今日统计
    private int todayScore;
    private int todayMessageCount;
    private int todayRank;
    
    // 本周统计
    private int weekScore;
    private int weekRank;
    
    // 本月统计
    private int monthScore;
    private int monthRank;
}
