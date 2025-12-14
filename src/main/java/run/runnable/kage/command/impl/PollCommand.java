package run.runnable.kage.command.impl;

import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import org.springframework.stereotype.Component;
import run.runnable.kage.command.CommandContext;
import run.runnable.kage.command.UnifiedCommand;
import run.runnable.kage.service.PollService;

import java.awt.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class PollCommand implements UnifiedCommand {

    private final PollService pollService;

    @Override
    public String getName() {
        return "投票";
    }

    @Override
    public String getDescription() {
        return "创建投票 (用法: 投票 标题 | 选项1 | 选项2 | ... | 分钟 [多选] [匿名])";
    }

    @Override
    public CommandData buildCommandData() {
        return Commands.slash("poll", "创建投票")
                // 必填参数必须在前面
                .addOption(OptionType.STRING, "title", "投票标题", true)
                .addOption(OptionType.STRING, "option1", "选项1", true)
                .addOption(OptionType.STRING, "option2", "选项2", true)
                .addOption(OptionType.INTEGER, "minutes", "持续时间（分钟）", true)
                // 可选参数
                .addOption(OptionType.STRING, "option3", "选项3", false)
                .addOption(OptionType.STRING, "option4", "选项4", false)
                .addOption(OptionType.STRING, "option5", "选项5", false)
                .addOption(OptionType.BOOLEAN, "multiple", "是否允许多选", false)
                .addOption(OptionType.BOOLEAN, "anonymous", "是否匿名", false);
    }

    @Override
    public void execute(CommandContext ctx) {
        if (!ctx.isFromGuild()) {
            ctx.replyEphemeral("❌ 该命令只能在服务器中使用");
            return;
        }

        String title;
        List<String> options = new ArrayList<>();
        int durationMinutes;
        boolean multipleChoice;
        boolean anonymous;

        // 尝试从 Slash 命令参数获取
        String slashTitle = ctx.getString("title");
        Integer slashMinutes = ctx.getInteger("minutes");

        if (slashTitle != null && slashMinutes != null) {
            title = slashTitle;
            durationMinutes = slashMinutes;
            multipleChoice = Boolean.TRUE.equals(ctx.getBoolean("multiple"));
            anonymous = Boolean.TRUE.equals(ctx.getBoolean("anonymous"));

            for (int i = 1; i <= 5; i++) {
                String opt = ctx.getString("option" + i);
                if (opt != null) {
                    options.add(opt);
                }
            }
        } else {
            // 从传统命令参数解析
            String rawArgs = ctx.getRawArgs();
            if (rawArgs == null || rawArgs.isBlank()) {
                ctx.reply("""
                        用法: `投票 标题 | 选项1 | 选项2 | ... | 分钟 [多选] [匿名]`
                        例如: `投票 今晚吃什么 | 火锅 | 烧烤 | 披萨 | 30`
                        多选: `投票 喜欢的颜色 | 红 | 蓝 | 绿 | 60 多选`
                        匿名: `投票 满意度调查 | 满意 | 一般 | 不满意 | 120 匿名`
                        """);
                return;
            }

            String[] parts = rawArgs.split("\\|");
            if (parts.length < 3) {
                ctx.reply("格式错误！至少需要标题和2个选项");
                return;
            }

            title = parts[0].trim();
            durationMinutes = 30;
            multipleChoice = false;
            anonymous = false;

            for (int i = 1; i < parts.length; i++) {
                String part = parts[i].trim();
                if (part.matches("\\d+")) {
                    durationMinutes = Integer.parseInt(part);
                } else if (part.equals("多选")) {
                    multipleChoice = true;
                } else if (part.equals("匿名")) {
                    anonymous = true;
                } else {
                    options.add(part);
                }
            }
        }

        if (options.size() < 2) {
            ctx.replyEphemeral("❌ 至少需要 2 个选项");
            return;
        }

        if (options.size() > 10) {
            ctx.replyEphemeral("❌ 最多支持 10 个选项");
            return;
        }

        LocalDateTime endTime = LocalDateTime.now().plusMinutes(durationMinutes);
        String guildId = ctx.getGuild().getId();
        String channelId = ctx.getChannel().getId();
        String creatorId = ctx.getUser().getId();

        boolean finalMultipleChoice = multipleChoice;
        boolean finalAnonymous = anonymous;
        String finalTitle = title;

        ctx.deferReply(hook -> {
            pollService.createPoll(guildId, channelId, creatorId, finalTitle, options, endTime, finalMultipleChoice, finalAnonymous)
                    .subscribe(poll -> {
                        pollService.getOptions(poll.getId())
                                .collectList()
                                .subscribe(pollOptions -> {
                                    String endTimeStr = endTime.format(DateTimeFormatter.ofPattern("MM-dd HH:mm"));

                                    EmbedBuilder embed = new EmbedBuilder()
                                            .setTitle("📊 " + finalTitle)
                                            .setColor(Color.BLUE)
                                            .setFooter("截止时间: " + endTimeStr + " | " +
                                                    (finalMultipleChoice ? "可多选" : "单选") + " | " +
                                                    (finalAnonymous ? "匿名" : "公开"));

                                    StringBuilder desc = new StringBuilder();
                                    List<Button> buttons = new ArrayList<>();

                                    for (int i = 0; i < pollOptions.size(); i++) {
                                        var opt = pollOptions.get(i);
                                        desc.append(pollService.getEmoji(i)).append(" ").append(opt.getContent()).append("\n");
                                        buttons.add(Button.secondary("poll_" + poll.getId() + "_" + opt.getId(),
                                                pollService.getEmoji(i)));
                                    }

                                    embed.setDescription(desc.toString());

                                    ctx.getChannel().sendMessageEmbeds(embed.build())
                                            .setActionRow(buttons)
                                            .queue(msg -> {
                                                pollService.updateMessageId(poll.getId(), msg.getId()).subscribe();
                                                hook.sendMessage("✅ 投票已创建！");
                                            });
                                });
                    });
        });
    }
}
