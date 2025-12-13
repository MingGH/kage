package run.runnable.kage.domain;

import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Data
@Builder
@Table("poll_option")
public class PollOption {

    @Id
    private Long id;

    @Column("poll_id")
    private Long pollId;

    @Column("option_index")
    private Integer optionIndex;

    @Column("content")
    private String content;
}
