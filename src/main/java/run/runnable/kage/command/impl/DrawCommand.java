package run.runnable.kage.command.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.utils.FileUpload;
import org.springframework.stereotype.Component;
import run.runnable.kage.command.CommandContext;
import run.runnable.kage.command.CommandContext.ReplyHook;
import run.runnable.kage.command.UnifiedCommand;
import run.runnable.kage.constants.AppConstant;
import run.runnable.kage.dto.GeneratedImage;
import run.runnable.kage.service.DrawRateLimiter;
import run.runnable.kage.service.SeedreamService;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;

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

        // 先同步 ack 交互；限流链在 boundedElastic 执行
        // 注意：tryAcquire 是 Mono<Void>，完成信号必须用 subscribe 的 completion 回调接收
        ctx.deferReply(hook ->
                drawRateLimiter.tryAcquire(userId)
                        .subscribeOn(Schedulers.boundedElastic())
                        .subscribe(
                                null,
                                err -> {
                                    String msg = err.getMessage() != null ? err.getMessage() : err.getClass().getSimpleName();
                                    log.info("/draw 限流拒绝: user={}, {}", userId, msg);
                                    hook.editMessage("🎨 " + msg);
                                },
                                () -> {
                                    log.info("/draw 限流通过: user={}", userId);
                                    draw(hook, ctx.getUser().getName(), prompt, size);
                                }),
                // 交互确认失败（如 connections 挂死超时）→ 降级为普通频道消息发送
                err -> fallbackToChannelMessage(ctx, ctx.getUser().getName(), prompt, size, userId));
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

    /**
     * 交互确认失败时的降级路径：用普通频道消息承载生成结果（绕开 interactions 端点）
     */
    private void fallbackToChannelMessage(CommandContext ctx, String userName, String prompt, String size, String userId) {
        log.warn("/draw 交互确认失败，降级为频道消息模式: user={}", userId);
        ctx.getChannel().sendMessage("🎨 **" + userName + "** 的画作生成中...").queue(
                msg -> drawRateLimiter.tryAcquire(userId)
                        .subscribeOn(Schedulers.boundedElastic())
                        .subscribe(
                                null,
                                err -> msg.editMessage("🎨 " + messageOf(err)).queue(),
                                () -> draw(replyHookOf(msg), userName, prompt, size)),
                err -> log.error("/draw 降级消息发送失败", err));
    }

    /**
     * 普通频道消息包装为 ReplyHook，复用既有生成与回复逻辑
     */
    private ReplyHook replyHookOf(Message msg) {
        return new ReplyHook() {
            @Override
            public void sendMessage(String response) {
                msg.editMessage(response).queue();
            }

            @Override
            public void editMessage(String response) {
                msg.editMessage(response).queue();
            }

            @Override
            public void editMessageWithImage(String message, byte[] imageBytes, String fileName, Runnable onImageFailure) {
                msg.editMessage(message)
                        .setFiles(List.of(FileUpload.fromData(imageBytes, fileName)))
                        .queue(null, err -> {
                            log.error("图片附件上传失败", err);
                            if (onImageFailure != null) onImageFailure.run();
                        });
            }
        };
    }

    private String messageOf(Throwable e) {
        return e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
    }
}
