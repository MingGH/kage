package run.runnable.kage.listener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.springframework.stereotype.Component;
import run.runnable.kage.domain.Poll;
import run.runnable.kage.service.EventDeduplicationService;
import run.runnable.kage.service.PollService;

@Slf4j
@Component
@RequiredArgsConstructor
public class PollButtonListener extends ListenerAdapter {

    private final PollService pollService;
    private final EventDeduplicationService deduplicationService;

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String buttonId = event.getComponentId();

        if (!buttonId.startsWith("poll_")) {
            return;
        }

        // 去重检查
        if (!deduplicationService.tryAcquire("button", event.getInteraction().getId())) {
            log.debug("按钮交互已被其他实例处理: {}", buttonId);
            return;
        }

        // 先立即响应
        event.deferReply(true).queue();

        // 解析 poll_投票ID_选项ID
        String[] parts = buttonId.split("_");
        if (parts.length != 3) {
            event.getHook().sendMessage("❌ 无效的投票").queue();
            return;
        }

        Long pollId = Long.parseLong(parts[1]);
        Long optionId = Long.parseLong(parts[2]);
        String userId = event.getUser().getId();
        String userName = event.getUser().getName();

        pollService.findById(pollId)
                .subscribe(poll -> {
                    if (poll == null) {
                        event.getHook().sendMessage("❌ 投票不存在").queue();
                        return;
                    }

                    if (!Poll.STATUS_ACTIVE.equals(poll.getStatus())) {
                        event.getHook().sendMessage("❌ 投票已结束").queue();
                        return;
                    }

                    pollService.vote(pollId, optionId, userId, userName, poll.getMultipleChoice())
                            .subscribe(success -> {
                                if (success) {
                                    event.getHook().sendMessage("✅ 投票成功！").queue();
                                } else {
                                    event.getHook().sendMessage("⚠️ 你已经投过这个选项了").queue();
                                }
                            });
                });
    }
}
