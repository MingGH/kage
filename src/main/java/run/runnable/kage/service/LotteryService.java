package run.runnable.kage.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import run.runnable.kage.domain.Lottery;
import run.runnable.kage.domain.LotteryParticipant;
import run.runnable.kage.repository.LotteryParticipantRepository;
import run.runnable.kage.repository.LotteryRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class LotteryService {

    private final LotteryRepository lotteryRepository;
    private final LotteryParticipantRepository participantRepository;
    private final ApplicationContext applicationContext;

    public Mono<Lottery> createLottery(String guildId, String channelId, String creatorId,
                                        String prize, int winnerCount, LocalDateTime endTime) {
        Lottery lottery = Lottery.builder()
                .guildId(guildId)
                .channelId(channelId)
                .creatorId(creatorId)
                .prize(prize)
                .winnerCount(winnerCount)
                .endTime(endTime)
                .status(Lottery.STATUS_ACTIVE)
                .createdAt(LocalDateTime.now())
                .build();
        return lotteryRepository.save(lottery);
    }

    public Mono<Void> updateMessageId(Long lotteryId, String messageId) {
        return lotteryRepository.updateMessageId(lotteryId, messageId);
    }

    public Mono<LotteryParticipant> participate(Long lotteryId, String userId, String userName) {
        return participantRepository.findByLotteryAndUser(lotteryId, userId)
                .switchIfEmpty(
                        participantRepository.save(LotteryParticipant.builder()
                                .lotteryId(lotteryId)
                                .userId(userId)
                                .userName(userName)
                                .isWinner(false)
                                .createdAt(LocalDateTime.now())
                                .build())
                );
    }

    public Mono<Long> getParticipantCount(Long lotteryId) {
        return participantRepository.countByLotteryId(lotteryId);
    }

    public Mono<Lottery> findById(Long id) {
        return lotteryRepository.findById(id);
    }

    /**
     * 定时检查并开奖
     */
    @Scheduled(fixedRate = 10000) // 每10秒检查一次
    public void checkAndDrawLotteries() {
        lotteryRepository.findExpiredLotteries(LocalDateTime.now())
                .flatMap(this::drawLottery)
                .subscribe();
    }

    private Mono<Void> drawLottery(Lottery lottery) {
        return participantRepository.findByLotteryId(lottery.getId())
                .collectList()
                .flatMap(participants -> {
                    // 随机抽取中奖者
                    List<LotteryParticipant> winners = selectWinners(participants, lottery.getWinnerCount());

                    // 更新中奖状态
                    return Mono.when(
                            winners.stream()
                                    .map(w -> {
                                        w.setIsWinner(true);
                                        return participantRepository.save(w);
                                    })
                                    .collect(Collectors.toList())
                    ).then(Mono.fromRunnable(() -> {
                        // 更新抽奖状态
                        lotteryRepository.updateStatus(lottery.getId(), Lottery.STATUS_ENDED).subscribe();
                        // 发送开奖消息
                        announceWinners(lottery, winners, participants.size());
                    }));
                });
    }

    private List<LotteryParticipant> selectWinners(List<LotteryParticipant> participants, int count) {
        if (participants.isEmpty()) {
            return new ArrayList<>();
        }
        List<LotteryParticipant> shuffled = new ArrayList<>(participants);
        Collections.shuffle(shuffled);
        return shuffled.subList(0, Math.min(count, shuffled.size()));
    }

    private void announceWinners(Lottery lottery, List<LotteryParticipant> winners, int totalParticipants) {
        DiscordBotService botService = applicationContext.getBean(DiscordBotService.class);
        if (!botService.isReady()) {
            log.error("Discord bot 未就绪，无法发送开奖消息");
            return;
        }
        TextChannel channel = botService.getJda().getTextChannelById(lottery.getChannelId());
        if (channel == null) {
            log.error("找不到频道: {}", lottery.getChannelId());
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("🎉 **抽奖结束！**\n");
        sb.append("奖品: ").append(lottery.getPrize()).append("\n");
        sb.append("参与人数: ").append(totalParticipants).append("\n\n");

        if (winners.isEmpty()) {
            sb.append("😢 没有人参与抽奖");
        } else {
            sb.append("🏆 **中奖者:**\n");
            for (LotteryParticipant winner : winners) {
                sb.append("- <@").append(winner.getUserId()).append(">\n");
            }
        }

        channel.sendMessage(sb.toString()).queue();
    }
}
