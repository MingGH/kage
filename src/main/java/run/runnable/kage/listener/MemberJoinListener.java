package run.runnable.kage.listener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Slf4j
@Component
@RequiredArgsConstructor
public class MemberJoinListener extends ListenerAdapter {

    // 可以改成从配置或数据库读取
    private static final String DEFAULT_ROLE_NAME = "成员";
    private static final String WELCOME_CHANNEL_NAME = "welcome";
    private static final String WELCOME_KEY_PREFIX = "kage:welcome:";
    private static final Duration WELCOME_DEDUP_TTL = Duration.ofMinutes(5);
    
    private final ReactiveStringRedisTemplate redisTemplate;

    @Override
    public void onGuildMemberJoin(GuildMemberJoinEvent event) {
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
        Role role = guild.getRolesByName(DEFAULT_ROLE_NAME, true).stream().findFirst().orElse(null);

        if (role != null) {
            guild.addRoleToMember(member, role).queue(
                    success -> log.info("已给 {} 添加身份组: {}", member.getUser().getName(), role.getName()),
                    error -> log.error("添加身份组失败: {}", error.getMessage())
            );
        } else {
            log.warn("找不到默认身份组: {}", DEFAULT_ROLE_NAME);
        }
    }

    private void sendWelcomeMessage(Guild guild, Member member) {
        // 优先找 welcome 频道，找不到就用系统频道
        TextChannel channel = guild.getTextChannelsByName(WELCOME_CHANNEL_NAME, true)
                .stream().findFirst()
                .orElse(guild.getSystemChannel());

        if (channel != null) {
            String welcomeMsg = String.format("""
                    🎉 欢迎 %s 加入 **%s**！
                    
                    希望你在这里玩得开心～ 有问题可以随时 @我 哦！
                    输入 `/help` 或 `@布布 help` 查看我能做什么 😊
                    """, member.getAsMention(), guild.getName());

            channel.sendMessage(welcomeMsg).queue();
        } else {
            log.warn("找不到欢迎频道，服务器: {}", guild.getName());
        }
    }
}
