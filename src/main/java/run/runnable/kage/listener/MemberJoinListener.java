package run.runnable.kage.listener;

import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Slf4j
@Component
public class MemberJoinListener extends ListenerAdapter {

    private static final String WELCOME_KEY_PREFIX = "kage:welcome:";
    private static final Duration WELCOME_DEDUP_TTL = Duration.ofMinutes(5);
    
    private final ReactiveStringRedisTemplate redisTemplate;
    private final boolean welcomeEnabled;
    private final String welcomeChannelName;
    private final String defaultRoleName;
    private final String welcomeMessage;

    public MemberJoinListener(
            ReactiveStringRedisTemplate redisTemplate,
            @Value("${discord.welcome.enabled:true}") boolean welcomeEnabled,
            @Value("${discord.welcome.channel-name:welcome}") String welcomeChannelName,
            @Value("${discord.welcome.default-role-name:}") String defaultRoleName,
            @Value("${discord.welcome.message:}") String welcomeMessage) {
        this.redisTemplate = redisTemplate;
        this.welcomeEnabled = welcomeEnabled;
        this.welcomeChannelName = welcomeChannelName;
        this.defaultRoleName = defaultRoleName;
        this.welcomeMessage = welcomeMessage;
    }

    @Override
    public void onGuildMemberJoin(GuildMemberJoinEvent event) {
        if (!welcomeEnabled) {
            return;
        }
        
        Guild guild = event.getGuild();
        Member member = event.getMember();
        
        String dedupKey = WELCOME_KEY_PREFIX + guild.getId() + ":" + member.getId();
        
        // 使用 Redis 去重，防止多实例重复发送
        redisTemplate.opsForValue()
                .setIfAbsent(dedupKey, "1", WELCOME_DEDUP_TTL)
                .subscribe(success -> {
                    if (Boolean.TRUE.equals(success)) {
                        log.info("新成员加入 - 服务器: {}, 用户: {}", guild.getName(), member.getUser().getName());
                        
                        // 给予默认身份组
                        assignDefaultRole(guild, member);
                        
                        // 发送欢迎消息
                        sendWelcomeMessage(guild, member);
                    } else {
                        log.debug("欢迎消息已被其他实例处理: guildId={}, userId={}", guild.getId(), member.getId());
                    }
                });
    }

    private void assignDefaultRole(Guild guild, Member member) {
        if (defaultRoleName == null || defaultRoleName.isBlank()) {
            return;
        }
        
        Role role = guild.getRolesByName(defaultRoleName, true).stream().findFirst().orElse(null);

        if (role != null) {
            guild.addRoleToMember(member, role).queue(
                    success -> log.info("已给 {} 添加身份组: {}", member.getUser().getName(), role.getName()),
                    error -> log.error("添加身份组失败: {}", error.getMessage())
            );
        } else {
            log.warn("找不到默认身份组: {}", defaultRoleName);
        }
    }

    private void sendWelcomeMessage(Guild guild, Member member) {
        if (welcomeMessage == null || welcomeMessage.isBlank()) {
            return;
        }
        
        // 优先找配置的频道，找不到就用系统频道
        TextChannel channel = guild.getTextChannelsByName(welcomeChannelName, true)
                .stream().findFirst()
                .orElse(guild.getSystemChannel());

        if (channel != null) {
            String msg = welcomeMessage
                    .replace("{mention}", member.getAsMention())
                    .replace("{server}", guild.getName())
                    .replace("{user}", member.getUser().getName());

            channel.sendMessage(msg).queue();
        } else {
            log.warn("找不到欢迎频道，服务器: {}", guild.getName());
        }
    }
}
