package run.runnable.kage.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.springframework.stereotype.Component;
import run.runnable.kage.command.SlashCommand;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class SlashCommandManager extends ListenerAdapter {

    private final List<SlashCommand> slashCommands;
    private final EventDeduplicationService deduplicationService;
    private final Map<String, SlashCommand> commandMap = new HashMap<>();

    @PostConstruct
    public void init() {
        for (SlashCommand command : slashCommands) {
            // 使用 buildCommandData 中的名称作为 key（Slash 命令名可能与传统命令名不同）
            String slashName = command.buildCommandData().getName();
            commandMap.put(slashName.toLowerCase(), command);
            log.info("注册 Slash 命令: /{}", slashName);
        }
    }

    /**
     * 注册所有 Slash 命令到 Discord
     */
    public void registerCommands(JDA jda) {
        var commandData = slashCommands.stream()
                .map(SlashCommand::buildCommandData)
                .toList();

        jda.updateCommands().addCommands(commandData).queue(
                success -> log.info("Slash 命令注册成功，共 {} 个", commandData.size()),
                error -> log.error("Slash 命令注册失败: {}", error.getMessage())
        );
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        String commandName = event.getName().toLowerCase();
        SlashCommand command = commandMap.get(commandName);

        if (command == null) {
            event.reply("❌ 未知命令").setEphemeral(true).queue();
            return;
        }

        // 使用统一去重服务
        if (deduplicationService.tryAcquire("slash", event.getInteraction().getId())) {
            log.info("执行 Slash 命令: /{} by {}", commandName, event.getUser().getName());
            command.execute(event);
        } else {
            log.debug("Slash 命令已被其他实例处理: /{}", commandName);
        }
    }
}
