package run.runnable.kage.domain;

import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Data
@Builder
@Table("poll")
public class Poll {

    @Id
    private Long id;

    @Column("guild_id")
    private String guildId;

    @Column("channel_id")
    private String channelId;

    @Column("message_id")
    private String messageId;

    @Column("creator_id")
    private String creatorId;

    @Column("title")
    private String title;

    @Column("end_time")
    private LocalDateTime endTime;

    @Column("multiple_choice")
    private Boolean multipleChoice;

    @Column("anonymous")
    private Boolean anonymous;

    @Column("status")
    private String status;

    @Column("created_at")
    private LocalDateTime createdAt;

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_ENDED = "ENDED";
}
