package run.runnable.kage.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "discord.bot")
public class DiscordConfig {
    private String token;
    private String activity;
}