package run.runnable.kage.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Service;
import run.runnable.kage.config.DiscordConfig;
import run.runnable.kage.listener.DiscordMessageListener;
import run.runnable.kage.listener.LotteryButtonListener;
import run.runnable.kage.listener.MemberJoinListener;
import run.runnable.kage.listener.PollButtonListener;

@Slf4j
@Service
@RequiredArgsConstructor
public class DiscordBotService implements CommandLineRunner {

    private final DiscordConfig discordConfig;
    private final DiscordMessageListener messageListener;
    private final LotteryButtonListener lotteryButtonListener;
    private final MemberJoinListener memberJoinListener;
    private final PollButtonListener pollButtonListener;
    private final SlashCommandManager slashCommandManager;
    private JDA jda;

    @Override
    public void run(String... args) throws Exception {
        if (discordConfig.getToken() == null || discordConfig.getToken().equals("YOUR_DISCORD_BOT_TOKEN_HERE")) {
            log.warn("Discord bot token not configured. Skipping Discord bot initialization.");
            return;
        }

        try {
            log.info("Starting Discord bot...");
            
            jda = JDABuilder.createDefault(discordConfig.getToken())
                    .setActivity(Activity.playing(discordConfig.getActivity()))
                    .enableIntents(
                            GatewayIntent.GUILD_MESSAGES,
                            GatewayIntent.MESSAGE_CONTENT,
                            GatewayIntent.DIRECT_MESSAGES,
                            GatewayIntent.GUILD_MEMBERS  // 需要开启才能监听成员加入事件
                    )
                    .addEventListeners(messageListener, lotteryButtonListener, memberJoinListener, pollButtonListener, slashCommandManager)
                    .build();

            jda.awaitReady();
            log.info("Discord bot started successfully! Bot is connected as: {}", jda.getSelfUser().getName());
            
            // 注册 Slash 命令
            slashCommandManager.registerCommands(jda);
            
        } catch (Exception e) {
            log.error("Failed to start Discord bot", e);
            throw e;
        }
    }

    public JDA getJda() {
        return jda;
    }

    public boolean isReady() {
        return jda != null && jda.getStatus() == JDA.Status.CONNECTED;
    }
}