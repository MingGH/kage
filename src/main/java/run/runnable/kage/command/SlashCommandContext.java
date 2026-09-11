package run.runnable.kage.command;

import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.utils.FileUpload;

import java.util.List;
import java.util.function.Consumer;

/**
 * Slash 命令的上下文实现
 */
@Slf4j
public class SlashCommandContext implements CommandContext {

    private final SlashCommandInteractionEvent event;

    public SlashCommandContext(SlashCommandInteractionEvent event) {
        this.event = event;
    }

    @Override
    public Guild getGuild() {
        return event.getGuild();
    }

    @Override
    public MessageChannel getChannel() {
        return event.getChannel();
    }

    @Override
    public User getUser() {
        return event.getUser();
    }

    @Override
    public boolean isFromGuild() {
        return event.isFromGuild();
    }

    @Override
    public String getString(String name) {
        OptionMapping opt = event.getOption(name);
        return opt != null ? opt.getAsString() : null;
    }

    @Override
    public Integer getInteger(String name) {
        OptionMapping opt = event.getOption(name);
        return opt != null ? (int) opt.getAsLong() : null;
    }

    @Override
    public Boolean getBoolean(String name) {
        OptionMapping opt = event.getOption(name);
        return opt != null ? opt.getAsBoolean() : null;
    }

    @Override
    public String getRawArgs() {
        return null; // Slash 命令没有原始参数
    }

    @Override
    public void reply(String message) {
        event.reply(message).queue();
    }

    @Override
    public void replyEphemeral(String message) {
        event.reply(message).setEphemeral(true).queue();
    }

    @Override
    public void deferReply(Consumer<ReplyHook> callback) {
        event.deferReply().queue(hook ->
                // 先发送一条初始消息
                hook.sendMessage("🤔 思考中...").queue(msg ->
                        callback.accept(new ReplyHook() {
                            @Override
                            public void sendMessage(String response) {
                                msg.editMessage(response).queue();
                            }

                            @Override
                            public void editMessage(String response) {
                                msg.editMessage(response).queue();
                            }

                            @Override
                            public void editMessageWithImage(String message, byte[] imageBytes, String fileName, Runnable onFailure) {
                                msg.editMessage(message)
                                        .setFiles(List.of(FileUpload.fromData(imageBytes, fileName)))
                                        .queue(null, err -> {
                                            log.error("图片附件上传失败", err);
                                            if (onFailure != null) onFailure.run();
                                        });
                            }
                        })));
    }

    public SlashCommandInteractionEvent getEvent() {
        return event;
    }
}
