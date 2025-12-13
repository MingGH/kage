package run.runnable.kage.listener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.springframework.stereotype.Component;
import run.runnable.kage.domain.Lottery;
import run.runnable.kage.service.LotteryService;

@Slf4j
@Component
@RequiredArgsConstructor
public class LotteryButtonListener extends ListenerAdapter {

    private final LotteryService lotteryService;

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String buttonId = event.getComponentId();

        if (!buttonId.startsWith("lottery_join_")) {
            return;
        }

        // 先立即响应，避免 3 秒超时
        event.deferReply(true).queue();

        Long lotteryId = Long.parseLong(buttonId.replace("lottery_join_", ""));
        String userId = event.getUser().getId();
        String userName = event.getUser().getName();

        lotteryService.findById(lotteryId)
                .subscribe(lottery -> {
                    if (lottery == null) {
                        event.getHook().sendMessage("❌ 抽奖活动不存在").queue();
                        return;
                    }

                    if (!Lottery.STATUS_ACTIVE.equals(lottery.getStatus())) {
                        event.getHook().sendMessage("❌ 抽奖已结束").queue();
                        return;
                    }

                    lotteryService.participate(lotteryId, userId, userName)
                            .flatMap(p -> lotteryService.getParticipantCount(lotteryId))
                            .subscribe(count -> {
                                event.getHook().sendMessage("✅ 参与成功！当前共 " + count + " 人参与").queue();
                            });
                });
    }
}
