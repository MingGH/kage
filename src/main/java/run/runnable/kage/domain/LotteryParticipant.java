package run.runnable.kage.domain;

import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Data
@Builder
@Table("lottery_participant")
public class LotteryParticipant {

    @Id
    private Long id;

    @Column("lottery_id")
    private Long lotteryId;

    @Column("user_id")
    private String userId;

    @Column("user_name")
    private String userName;

    @Column("is_winner")
    private Boolean isWinner;

    @Column("created_at")
    private LocalDateTime createdAt;
}
