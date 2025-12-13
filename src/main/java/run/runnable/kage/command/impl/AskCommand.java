package run.runnable.kage.command.impl;

import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import org.springframework.stereotype.Component;
import run.runnable.kage.command.CommandContext;
import run.runnable.kage.command.UnifiedCommand;
import run.runnable.kage.service.DeepSeekService;

@Component
@RequiredArgsConstructor
public class AskCommand implements UnifiedCommand {

    private final DeepSeekService deepSeekService;

    @Override
    public String getName() {
        return "ask";
    }

    @Override
    public String getDescription() {
        return "向 AI 提问";
    }

    @Override
    public CommandData buildCommandData() {
        return Commands.slash(getName(), getDescription())
                .addOption(OptionType.STRING, "question", "你的问题", true);
    }

    @Override
    public void execute(CommandContext ctx) {
        // 获取问题：Slash 命令从 option 获取，传统命令从 rawArgs 获取
        String question = ctx.getString("question");
        if (question == null || question.isBlank()) {
            question = ctx.getRawArgs();
        }

        if (question == null || question.isBlank()) {
            ctx.replyEphemeral("请输入问题，例如: `/ask 今天天气怎么样`");
            return;
        }

        if (!ctx.isFromGuild()) {
            ctx.replyEphemeral("❌ 该命令只能在服务器中使用");
            return;
        }

        String guildId = ctx.getGuild().getId();
        String userId = ctx.getUser().getId();
        String finalQuestion = question;

        ctx.deferReply(hook -> {
            deepSeekService.chat(guildId, userId, finalQuestion)
                    .subscribe(
                            answer -> {
                                String reply = answer.length() > 1900
                                        ? answer.substring(0, 1900) + "..."
                                        : answer;
                                hook.sendMessage(reply);
                            },
                            error -> hook.sendMessage("❌ 出错了: " + error.getMessage())
                    );
        });
    }
}
