package run.runnable.kage.command.impl;

import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.springframework.stereotype.Component;
import run.runnable.kage.command.Command;
import run.runnable.kage.service.DeepSeekService;

@Component
@RequiredArgsConstructor
public class AskCommand implements Command {

    private final DeepSeekService deepSeekService;

    @Override
    public String getName() {
        return "ask";
    }

    @Override
    public String getDescription() {
        return "向 AI 提问 (用法: !ask 你的问题)";
    }

    @Override
    public void execute(MessageReceivedEvent event, String[] args) {
        if (args.length == 0) {
            event.getChannel().sendMessage("请输入问题，例如: `!ask 今天天气怎么样`").queue();
            return;
        }

        if (!event.isFromGuild()) {
            event.getChannel().sendMessage("❌ 该命令只能在服务器中使用").queue();
            return;
        }

        String question = String.join(" ", args);
        String guildId = event.getGuild().getId();
        String userId = event.getAuthor().getId();
        
        // 先发送一个"思考中"的提示
        event.getChannel().sendMessage("🤔 思考中...")
                .queue(thinkingMsg -> {
            // 调用 AI 服务
            deepSeekService.chat(guildId, userId, question)
                    .subscribe(
                            answer -> {
                                // 删除"思考中"消息，发送回答
                                thinkingMsg.delete().queue();
                                // Discord 消息限制 2000 字符
                                String reply = answer.length() > 1900 
                                        ? answer.substring(0, 1900) + "..." 
                                        : answer;
                                event.getChannel().sendMessage(reply).queue();
                            },
                            error -> {
                                thinkingMsg.delete().queue();
                                event.getChannel().sendMessage("❌ 出错了: " + error.getMessage()).queue();
                            }
                    );
        });
    }
}
