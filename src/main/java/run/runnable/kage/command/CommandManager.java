package run.runnable.kage.command;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import run.runnable.kage.service.DeepSeekService;

import java.time.Duration;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 命令管理器 - 负责分发 @机器人 命令
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CommandManager {

    // Discord 不支持的 markdown 分隔线（匹配前后的换行符）
    private static final Pattern HORIZONTAL_RULE = Pattern.compile("\\n*(-{3,}|\\*{3,}|_{3,})\\n*");

    private static final String EVENT_KEY_PREFIX = "discord:event:";
    private static final Duration EVENT_EXPIRE = Duration.ofMinutes(5);

    private final CommandRegistry commandRegistry;
    private final ReactiveStringRedisTemplate redisTemplate;
    private final DeepSeekService deepSeekService;

    /**
     * 处理 @机器人 消息
     * 使用 Redis 防止重复处理（本地开发和线上同时运行时）
     */
    public void handleMessage(MessageReceivedEvent event) {
        // 只处理 @机器人 的消息
        if (!event.getMessage().getMentions().isMentioned(event.getJDA().getSelfUser())) {
            return;
        }

        String content = event.getMessage().getContentRaw().trim();
        // 移除 @机器人 部分
        String commandContent = content.replaceFirst("<@!?" + event.getJDA().getSelfUser().getId() + ">\\s*", "").trim();

        // 用消息 ID 作为去重 key
        String eventKey = EVENT_KEY_PREFIX + event.getMessageId();

        redisTemplate.opsForValue()
                .setIfAbsent(eventKey, "1", EVENT_EXPIRE)
                .subscribe(success -> {
                    if (Boolean.TRUE.equals(success)) {
                        executeCommand(event, commandContent);
                    } else {
                        log.debug("事件已被其他实例处理: {}", event.getMessageId());
                    }
                });
    }

    private void executeCommand(MessageReceivedEvent event, String commandContent) {
        // 如果 @机器人 后面没有内容，当作打招呼
        if (commandContent.isBlank()) {
            chatWithAI(event, "你好");
            return;
        }

        String[] parts = commandContent.split("\\s+");
        String commandName = parts[0].toLowerCase();
        Command cmd = commandRegistry.getCommand(commandName);

        // 如果没有匹配到命令，直接当作 AI 对话
        if (cmd == null) {
            chatWithAI(event, commandContent);
            return;
        }

        String[] args = new String[parts.length - 1];
        System.arraycopy(parts, 1, args, 0, args.length);
        log.info("执行命令: {} by {}", commandName, event.getAuthor().getName());
        cmd.execute(event, args);
    }

    /**
     * 调用 DeepSeek AI 进行对话（流式响应）
     */
    private void chatWithAI(MessageReceivedEvent event, String message) {
        if (!event.isFromGuild()) {
            event.getMessage().reply("❌ 该功能只能在服务器中使用").queue();
            return;
        }

        String guildId = event.getGuild().getId();
        String userId = event.getAuthor().getId();

        // 先回复一条消息，后续流式更新
        event.getMessage().reply("🤔 思考中...").queue(replyMsg -> {
            StringBuilder contentBuilder = new StringBuilder();
            
            deepSeekService.chatStream(guildId, userId, message, null)
                    // 节流：每 500ms 更新一次，避免触发 Discord 速率限制
                    .buffer(java.time.Duration.ofMillis(500))
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
                                replyMsg.editMessage(displayContent + "\n\n`✍️ 输入中...`").queue();
                            },
                            error -> replyMsg.editMessage("❌ 出错了: " + error.getMessage()).queue(),
                            () -> {
                                // 完成时移除打字指示器，格式化输出
                                String finalContent = formatForDiscord(contentBuilder.toString());
                                String displayContent = finalContent.length() > 1900
                                        ? finalContent.substring(0, 1900) + "..."
                                        : finalContent;
                                replyMsg.editMessage(displayContent).queue();
                            }
                    );
        });
    }

    public Map<String, Command> getCommands() {
        return commandRegistry.getCommandMap();
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
