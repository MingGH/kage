package run.runnable.kage.command.impl;

import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.entities.GuildVoiceState;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import org.springframework.stereotype.Component;
import run.runnable.kage.command.CommandContext;
import run.runnable.kage.command.MessageCommandContext;
import run.runnable.kage.command.SlashCommandContext;
import run.runnable.kage.command.UnifiedCommand;
import run.runnable.kage.service.MusicService;

/**
 * 播放音乐命令
 */
@Component
@RequiredArgsConstructor
public class PlayCommand implements UnifiedCommand {

    private final MusicService musicService;

    @Override
    public String getName() {
        return "play";
    }

    @Override
    public String getDescription() {
        return "播放音乐（支持 URL 或网易云歌曲 ID）";
    }

    @Override
    public CommandData buildCommandData() {
        return Commands.slash(getName(), getDescription())
                .addOption(OptionType.STRING, "url", "音乐 URL 或网易云歌曲 ID", true);
    }

    @Override
    public void execute(MessageReceivedEvent event, String[] args) {
        MessageCommandContext ctx = new MessageCommandContext(event, args);
        if (args.length > 0) {
            ctx.setArg("url", String.join(" ", args));
        }
        execute(ctx);
    }

    @Override
    public void execute(CommandContext ctx) {
        if (!ctx.isFromGuild()) {
            ctx.replyEphemeral("❌ 该命令只能在服务器中使用");
            return;
        }

        String input = ctx.getString("url");
        if (input == null || input.isBlank()) {
            ctx.reply("用法: `/play <URL>` 或 `/play <网易云歌曲ID>`\n例如: `/play 2599502751`");
            return;
        }

        // 获取用户所在的语音频道
        Member member = getMember(ctx);
        if (member == null) {
            ctx.reply("❌ 无法获取用户信息");
            return;
        }

        GuildVoiceState voiceState = member.getVoiceState();
        if (voiceState == null || !voiceState.inAudioChannel()) {
            ctx.reply("❌ 请先加入一个语音频道");
            return;
        }

        VoiceChannel voiceChannel = (VoiceChannel) voiceState.getChannel();
        if (voiceChannel == null) {
            ctx.reply("❌ 请先加入一个语音频道");
            return;
        }

        // 处理输入，支持网易云歌曲 ID
        String trackUrl = parseInput(input);

        // 加入语音频道
        if (!ctx.getGuild().getAudioManager().isConnected()) {
            if (!musicService.joinVoiceChannel(ctx.getGuild(), voiceChannel)) {
                ctx.reply("❌ 无法加入语音频道");
                return;
            }
        }

        // 播放音乐
        ctx.deferReply(hook -> {
            musicService.loadAndPlay(ctx.getGuild(), trackUrl, hook::sendMessage);
        });
    }

    private String parseInput(String input) {
        //FIXME 如果是纯数字，当作网易云歌曲 ID
//        if (input.matches("\\d+")) {
//            return "https://music.163.com/song/media/outer/url?id=" + input + ".mp3";
//        }
        // 否则当作 URL
        return input;
    }

    private Member getMember(CommandContext ctx) {
        if (ctx instanceof SlashCommandContext slashCtx) {
            return slashCtx.getEvent().getMember();
        } else if (ctx instanceof MessageCommandContext) {
            return ctx.getGuild().getMember(ctx.getUser());
        }
        return null;
    }
}
