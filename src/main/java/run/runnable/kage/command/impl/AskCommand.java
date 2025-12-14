package run.runnable.kage.command.impl;

import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import run.runnable.kage.command.CommandContext;
import run.runnable.kage.command.UnifiedCommand;
import run.runnable.kage.service.DeepSeekService;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

@Component
public class AskCommand implements UnifiedCommand {

    // Discord 不支持的 markdown 分隔线（匹配前后的换行符）
    private static final Pattern HORIZONTAL_RULE = Pattern.compile("\\n*(-{3,}|\\*{3,}|_{3,})\\n*");

    @Lazy
    @Autowired
    private DeepSeekService deepSeekService;

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

        // 检查用户是否正在处理中
        if (deepSeekService.isUserProcessing(guildId, userId)) {
            ctx.replyEphemeral("⏳ 请等待上一个问题回复完成");
            return;
        }

        ctx.deferReply(hook -> {
            StringBuilder contentBuilder = new StringBuilder();
            AtomicReference<String> lastSentContent = new AtomicReference<>("");
            
            deepSeekService.chatStream(guildId, userId, finalQuestion, null)
                    // 节流：每 500ms 更新一次，避免触发 Discord 速率限制
                    .buffer(Duration.ofMillis(500))
                    .subscribe(
                            chunks -> {
                                // 合并这段时间内的所有 chunk
                                chunks.forEach(contentBuilder::append);
                                String currentContent = formatForDiscord(contentBuilder.toString());
                                
                                // 截断过长内容（预留空间给提示）
                                String displayContent = currentContent.length() > 1850
                                        ? currentContent.substring(0, 1850) + "..."
                                        : currentContent;
                                
                                // 添加输入中提示
                                String messageToSend = displayContent + "\n\n`✍️ 输入中...`";
                                
                                // 只有内容变化时才更新
                                if (!messageToSend.equals(lastSentContent.get())) {
                                    lastSentContent.set(messageToSend);
                                    hook.editMessage(messageToSend);
                                }
                            },
                            error -> hook.editMessage("❌ 出错了: " + error.getMessage()),
                            () -> {
                                // 完成时移除打字指示器，格式化输出
                                String finalContent = formatForDiscord(contentBuilder.toString());
                                String displayContent = finalContent.length() > 1900
                                        ? finalContent.substring(0, 1900) + "..."
                                        : finalContent;
                                hook.editMessage(displayContent);
                            }
                    );
        });
    }

    /**
     * 格式化 AI 输出，移除 Discord 不支持的 markdown
     */
    private String formatForDiscord(String content) {
        if (content == null) return "";
        // 替换分隔线及其前后换行为单个换行
        return HORIZONTAL_RULE.matcher(content).replaceAll("\n");
    }
}
