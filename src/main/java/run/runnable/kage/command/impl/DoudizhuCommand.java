package run.runnable.kage.command.impl;

import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import org.springframework.stereotype.Component;
import run.runnable.kage.command.CommandContext;
import run.runnable.kage.command.SlashCommandContext;
import run.runnable.kage.command.UnifiedCommand;
import run.runnable.kage.domain.doudizhu.DoudizhuGame;
import run.runnable.kage.service.DoudizhuService;

@Component
@RequiredArgsConstructor
public class DoudizhuCommand implements UnifiedCommand {

    private final DoudizhuService doudizhuService;

    @Override
    public String getName() {
        return "斗地主";
    }

    @Override
    public String getDescription() {
        return "开始一局斗地主游戏";
    }

    @Override
    public CommandData buildCommandData() {
        return Commands.slash("doudizhu", "开始一局斗地主游戏");
    }

    @Override
    public void execute(CommandContext ctx) {
        if (!ctx.isFromGuild()) {
            ctx.replyEphemeral("❌ 该命令只能在服务器中使用");
            return;
        }

        String channelId = ctx.getChannel().getId();
        String userId = ctx.getUser().getId();
        String userName = ctx.getUser().getName();

        // 检查是否已有游戏
        DoudizhuGame existingGame = doudizhuService.getGame(channelId);
        if (existingGame != null) {
            ctx.replyEphemeral("❌ 当前频道已有进行中的游戏");
            return;
        }

        // 创建新游戏
        DoudizhuGame game = doudizhuService.createGame(channelId, userId, userName);
        if (game == null) {
            ctx.replyEphemeral("❌ 创建游戏失败");
            return;
        }

        String message = """
                🎴 **斗地主**
                
                %s 发起了一局斗地主！
                
                等待玩家加入 (1/3)
                - %s
                
                点击下方按钮加入游戏！
                """.formatted(userName, userName);

        // 使用 SlashCommandContext 发送带按钮的消息
        if (ctx instanceof SlashCommandContext slashCtx) {
            slashCtx.getEvent().reply(message)
                    .addActionRow(
                            Button.primary("ddz_join", "加入游戏").withEmoji(Emoji.fromUnicode("🎮")),
                            Button.danger("ddz_cancel", "取消游戏").withEmoji(Emoji.fromUnicode("❌"))
                    )
                    .queue();
        } else {
            // 传统命令，在频道发送
            ctx.getChannel().sendMessage(message)
                    .setActionRow(
                            Button.primary("ddz_join", "加入游戏").withEmoji(Emoji.fromUnicode("🎮")),
                            Button.danger("ddz_cancel", "取消游戏").withEmoji(Emoji.fromUnicode("❌"))
                    )
                    .queue();
        }
    }
}
