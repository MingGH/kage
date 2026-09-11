package run.runnable.kage.command;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;

import java.util.Map;
import java.util.function.Consumer;

/**
 * 统一的命令上下文，屏蔽传统命令和 Slash 命令的差异
 */
public interface CommandContext {

    /**
     * 获取服务器
     */
    Guild getGuild();

    /**
     * 获取频道
     */
    MessageChannel getChannel();

    /**
     * 获取用户
     */
    User getUser();

    /**
     * 是否来自服务器
     */
    boolean isFromGuild();

    /**
     * 获取字符串参数
     */
    String getString(String name);

    /**
     * 获取整数参数
     */
    Integer getInteger(String name);

    /**
     * 获取布尔参数
     */
    Boolean getBoolean(String name);

    /**
     * 获取所有原始参数（传统命令用）
     */
    String getRawArgs();

    /**
     * 回复消息
     */
    void reply(String message);

    /**
     * 回复消息（仅自己可见）
     */
    void replyEphemeral(String message);

    /**
     * 延迟回复（用于耗时操作）
     */
    void deferReply(Consumer<ReplyHook> callback);

    /**
     * 延迟回复的钩子
     */
    interface ReplyHook {
        void sendMessage(String message);

        /**
         * 编辑已发送的消息（用于流式更新）
         */
        void editMessage(String message);

        /**
         * 编辑消息并附带图片（图片字节作为附件上传到频道）
         */
        void editMessageWithImage(String message, byte[] imageBytes, String fileName, Runnable onFailure);

        /**
         * 同上，无失败回调的便捷重载
         */
        default void editMessageWithImage(String message, byte[] imageBytes, String fileName) {
            editMessageWithImage(message, imageBytes, fileName, null);
        }
    }
}
