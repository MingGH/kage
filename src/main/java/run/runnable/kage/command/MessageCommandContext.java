package run.runnable.kage.command;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 传统消息命令的上下文实现
 */
public class MessageCommandContext implements CommandContext {

    private final MessageReceivedEvent event;
    private final String[] args;
    private final Map<String, String> namedArgs = new HashMap<>();

    public MessageCommandContext(MessageReceivedEvent event, String[] args) {
        this.event = event;
        this.args = args;
    }

    /**
     * 设置命名参数（用于解析后的参数）
     */
    public void setArg(String name, String value) {
        namedArgs.put(name, value);
    }

    @Override
    public Guild getGuild() {
        return event.isFromGuild() ? event.getGuild() : null;
    }

    @Override
    public MessageChannel getChannel() {
        return event.getChannel();
    }

    @Override
    public User getUser() {
        return event.getAuthor();
    }

    @Override
    public boolean isFromGuild() {
        return event.isFromGuild();
    }

    @Override
    public String getString(String name) {
        return namedArgs.get(name);
    }

    @Override
    public Integer getInteger(String name) {
        String value = namedArgs.get(name);
        return value != null ? Integer.parseInt(value) : null;
    }

    @Override
    public Boolean getBoolean(String name) {
        String value = namedArgs.get(name);
        return value != null ? Boolean.parseBoolean(value) : null;
    }

    @Override
    public String getRawArgs() {
        return String.join(" ", args);
    }

    public String[] getArgs() {
        return args;
    }

    @Override
    public void reply(String message) {
        // 引用回复原消息
        event.getMessage().reply(message).queue();
    }

    @Override
    public void replyEphemeral(String message) {
        // 传统命令没有私密回复，直接引用回复
        event.getMessage().reply(message).queue();
    }

    @Override
    public void deferReply(Consumer<ReplyHook> callback) {
        event.getMessage().reply("🤔 处理中...").queue(msg -> {
            callback.accept(response -> {
                msg.delete().queue();
                event.getMessage().reply(response).queue();
            });
        });
    }
}
