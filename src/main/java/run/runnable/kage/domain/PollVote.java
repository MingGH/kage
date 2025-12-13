package run.runnable.kage.domain;

import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Data
@Builder
@Table("poll_vote")
public class PollVote {

    @Id
    private Long id;

    @Column("poll_id")
    private Long pollId;

    @Column("option_id")
    private Long optionId;

    @Column("user_id")
    private String userId;

    @Column("user_name")
    private String userName;

    @Column("created_at")
    private LocalDateTime createdAt;
}
