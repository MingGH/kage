package run.runnable.kage.domain;

import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@Table("slacking_daily_stats")
public class SlackingDailyStats {

    @Id
    private Long id;

    @Column("guild_id")
    private String guildId;

    @Column("user_id")
    private String userId;

    @Column("user_name")
    private String userName;

    @Column("stat_date")
    private LocalDate statDate;

    @Column("message_count")
    private Integer messageCount;

    @Column("total_score")
    private Integer totalScore;

    @Column("created_at")
    private LocalDateTime createdAt;

    @Column("updated_at")
    private LocalDateTime updatedAt;
}
