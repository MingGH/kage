package run.runnable.kage.domain;

import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Data
@Builder
@Table("chat_message")
public class ChatMessage {

    @Id
    private Long id;

    @Column("guild_id")
    private String guildId;

    @Column("user_id")
    private String userId;

    @Column("role")
    private String role;

    @Column("content")
    private String content;

    @Column("deleted")
    private Boolean deleted;

    @Column("created_at")
    private LocalDateTime createdAt;
}
