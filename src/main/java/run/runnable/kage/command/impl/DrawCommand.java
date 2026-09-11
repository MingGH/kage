package run.runnable.kage.command.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.springframework.stereotype.Component;
import run.runnable.kage.command.CommandContext;
import run.runnable.kage.command.CommandContext.ReplyHook;
import run.runnable.kage.command.UnifiedCommand;
import run.runnable.kage.constants.AppConstant;
import run.runnable.kage.dto.GeneratedImage;
import run.runnable.kage.service.DrawRateLimiter;
import run.runnable.kage.service.SeedreamService;
import reactor.core.publisher.Mono;

/**
 * /draw AI 画图命令：豆包 Seedream 生成图片并回复到频道
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DrawCommand implements UnifiedCommand {

    private static final int MAX_PROMPT_LENGTH = AppConstant.MAX_PROMPT_LENGTH;

    private final SeedreamService seedreamService;
    private final DrawRateLimiter drawRateLimiter;

    @Override
    public String getName() {
        return "draw";
    }

    @Override
    public String getDescription() {
        return "AI 画图（每日全限 100 张，每人 30 张）";
    }

    @Override
    public CommandData buildCommandData() {
        return Commands.slash(getName(), getDescription())
                .addOptions(
                        new OptionData(OptionType.STRING, "prompt", "画面描述", true),
                        new OptionData(OptionType.STRING, "size", "图片尺寸（默认 2K）", false)
                                .addChoice("1K (1024x1024)", "1K")
                                .addChoice("2K (2048x2048)", "2K"));
    }

    @Override
    public void execute(CommandContext ctx) {
        String error = validate(ctx);
        if (error != null) {
            ctx.replyEphemeral(error);
            return;
        }
        String prompt = resolvePrompt(ctx);
        String size = ctx.getString("size");
        String userId = ctx.getUser().getId();
        log.info("/draw 收到请求: user={}, size={}", userId, size);

        // 先同步 ack 交互（与 AskCommand 同款模式），Reactor 链全部在回调内执行
        ctx.deferReply(hook ->
                drawRateLimiter.tryAcquire(userId).subscribe(
                        v -> {
                            log.info("/draw 限流通过: user={}", userId);
                            draw(hook, ctx.getUser().getName(), prompt, size);
                        },
                        err -> {
                            String msg = err.getMessage() != null ? err.getMessage() : err.getClass().getSimpleName();
                            log.info("/draw 限流拒绝: user={}, {}", userId, msg);
                            hook.editMessage("🎨 " + msg);
                        }));
    }

    /**
     * 参数校验，返回错误提示；null 表示通过
     */
    private String validate(CommandContext ctx) {
        String prompt = resolvePrompt(ctx);
        if (prompt == null || prompt.isBlank()) {
            return "请输入画面描述，例如: `/draw prompt:一只在服务器上摸鱼的忍者`";
        }
        if (prompt.length() > MAX_PROMPT_LENGTH) {
            return "画面描述太长了（最多 " + MAX_PROMPT_LENGTH + " 字符）";
        }
        if (!ctx.isFromGuild()) {
            return "该命令只能在服务器中使用";
        }
        return null;
    }

    /**
     * prompt：Slash 取 option，传统命令取 rawArgs
     */
    private String resolvePrompt(CommandContext ctx) {
        String prompt = ctx.getString("prompt");
        return (prompt == null || prompt.isBlank()) ? ctx.getRawArgs() : prompt;
    }

    /**
     * 生成图片并回填到占位消息；下载失败时降级为仅回复 URL
     */
    private void draw(ReplyHook hook, String userName, String prompt, String size) {
        log.info("/draw 开始生成: user={}, size={}", userName, size);
        seedreamService.generateImage(prompt, size)
                .flatMap(this::withBytes)
                .subscribe(
                        outcome -> deliver(hook, userName, prompt, outcome),
                        err -> {
                            String msg = err.getMessage() != null ? err.getMessage() : err.getClass().getSimpleName();
                            log.error("/draw 生成失败: {}", msg, err);
                            hook.editMessage("❌ 画图失败: " + msg);
                        });
    }

    /**
     * 生成结果附带图片字节；下载失败用 null 字节标记，走 URL 降级
     */
    private Mono<DrawOutcome> withBytes(GeneratedImage image) {
        return Mono.defer(() -> seedreamService.downloadImage(image.url()))
                .map(bytes -> new DrawOutcome(image, bytes))
                .onErrorReturn(new DrawOutcome(image, null));
    }

    /**
     * 有字节则图片附件回复，上传失败降级为文本 + 链接
     */
    private void deliver(ReplyHook hook, String userName, String prompt, DrawOutcome outcome) {
        String text = "🎨 **" + userName + "** 的 AI 画作\n> " + prompt;
        String fallback = text + "\n" + outcome.image().url();
        if (outcome.hasImage()) {
            hook.editMessageWithImage(text, outcome.bytes(),
                    "draw-" + System.currentTimeMillis() + ".png",
                    () -> hook.editMessage(fallback));
        } else {
            hook.editMessage(fallback);
        }
    }

    /**
     * 生成结果：图片元数据 + 可选的字节内容
     */
    private record DrawOutcome(GeneratedImage image, byte[] bytes) {
        boolean hasImage() {
            return bytes != null && bytes.length > 0;
        }
    }
}
