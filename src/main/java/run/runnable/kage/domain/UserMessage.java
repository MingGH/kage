package run.runnable.kage.domain;

import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Data
@Builder
@Table("user_message")
public class UserMessage {

    @Id
    private Long id;

    @Column("guild_id")
    private String guildId;

    @Column("channel_id")
    private String channelId;

    @Column("user_id")
    private String userId;

    @Column("user_name")
    private String userName;

    @Column("content")
    private String content;

    @Column("message_id")
    private String messageId;

    @Column("created_at")
    private LocalDateTime createdAt;
}
