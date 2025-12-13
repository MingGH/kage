package run.runnable.kage.domain;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Data
@Table("cet_words")
public class CetWord {

    @Id
    private Integer id;

    @Column("data")
    private String data; // jsonb cast to text in query
}