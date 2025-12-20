package run.runnable.kage.listener;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.requests.restaction.AuditableRestAction;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MemberJoinListenerTest {

    @Mock
    private ReactiveStringRedisTemplate redisTemplate;
    @Mock
    private ReactiveValueOperations<String, String> valueOperations;

    @Mock
    private GuildMemberJoinEvent event;
    @Mock
    private Guild guild;
    @Mock
    private Member member;
    @Mock
    private User user;
    @Mock
    private Role role;
    @Mock
    private AuditableRestAction<Void> addRoleAction;
    @Mock
    private TextChannel channel;
    @Mock
    private MessageCreateAction messageAction;

    private MemberJoinListener listener;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        listener = new MemberJoinListener(redisTemplate, true, "welcome", "Member", "Welcome {user}!");
    }

    @Test
    @DisplayName("新成员加入 - 正常流程")
    void onGuildMemberJoin_success() {
        // Mock Event
        when(event.getGuild()).thenReturn(guild);
        when(event.getMember()).thenReturn(member);
        when(guild.getId()).thenReturn("g1");
        when(guild.getName()).thenReturn("Test Guild");
        when(member.getId()).thenReturn("u1");
        when(member.getUser()).thenReturn(user);
        when(user.getName()).thenReturn("User1");
        // replace() requires non-null replacement even if target not found
        lenient().when(member.getAsMention()).thenReturn("<@u1>");

        // Mock Redis Dedup
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(Mono.just(true));

        // Mock Role Assignment
        when(guild.getRolesByName("Member", true)).thenReturn(Collections.singletonList(role));
        when(role.getName()).thenReturn("Member");
        when(guild.addRoleToMember(member, role)).thenReturn(addRoleAction);

        // Mock Welcome Message
        when(guild.getTextChannelsByName("welcome", true)).thenReturn(Collections.singletonList(channel));
        when(channel.sendMessage(anyString())).thenReturn(messageAction);

        listener.onGuildMemberJoin(event);

        verify(guild).addRoleToMember(member, role);
        verify(channel).sendMessage(contains("Welcome User1!"));
    }

    @Test
    @DisplayName("新成员加入 - 去重拦截")
    void onGuildMemberJoin_duplicate() {
        when(event.getGuild()).thenReturn(guild);
        when(event.getMember()).thenReturn(member);
        when(guild.getId()).thenReturn("g1");
        when(member.getId()).thenReturn("u1");

        // Mock Redis Dedup returns false
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(Mono.just(false));

        listener.onGuildMemberJoin(event);

        verify(guild, never()).addRoleToMember(any(), any());
        verify(guild, never()).getTextChannelsByName(anyString(), anyBoolean());
    }
}
