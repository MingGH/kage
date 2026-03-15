package run.runnable.kage.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LeaderboardEntry {
    
    private int rank;
    private String userId;
    private String userName;
    private String avatarUrl;
    private int messageCount;
    private int totalScore;
}
