package run.runnable.kage.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.runnable.kage.domain.Poll;
import run.runnable.kage.domain.PollOption;
import run.runnable.kage.domain.PollVote;
import run.runnable.kage.repository.PollOptionRepository;
import run.runnable.kage.repository.PollRepository;
import run.runnable.kage.repository.PollVoteRepository;

import java.awt.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
@RequiredArgsConstructor
public class PollService {

    private final PollRepository pollRepository;
    private final PollOptionRepository optionRepository;
    private final PollVoteRepository voteRepository;
    private final ApplicationContext applicationContext;

    private static final String[] EMOJI_NUMBERS = {"1️⃣", "2️⃣", "3️⃣", "4️⃣", "5️⃣", "6️⃣", "7️⃣", "8️⃣", "9️⃣", "🔟"};

    public Mono<Poll> createPoll(String guildId, String channelId, String creatorId,
                                  String title, List<String> options, LocalDateTime endTime,
                                  boolean multipleChoice, boolean anonymous) {
        Poll poll = Poll.builder()
                .guildId(guildId)
                .channelId(channelId)
                .creatorId(creatorId)
                .title(title)
                .endTime(endTime)
                .multipleChoice(multipleChoice)
                .anonymous(anonymous)
                .status(Poll.STATUS_ACTIVE)
                .createdAt(LocalDateTime.now())
                .build();

        return pollRepository.save(poll)
                .flatMap(savedPoll -> {
                    // 保存选项
                    AtomicLong index = new AtomicLong(0);
                    return Flux.fromIterable(options)
                            .flatMap(content -> optionRepository.save(PollOption.builder()
                                    .pollId(savedPoll.getId())
                                    .optionIndex((int) index.getAndIncrement())
                                    .content(content)
                                    .build()))
                            .then(Mono.just(savedPoll));
                });
    }

    public Mono<Void> updateMessageId(Long pollId, String messageId) {
        return pollRepository.updateMessageId(pollId, messageId);
    }

    public Mono<Poll> findById(Long id) {
        return pollRepository.findById(id);
    }

    public Flux<PollOption> getOptions(Long pollId) {
        return optionRepository.findByPollId(pollId);
    }

    public Mono<Long> getVoteCount(Long optionId) {
        return voteRepository.countByOptionId(optionId);
    }

    public Mono<Boolean> vote(Long pollId, Long optionId, String userId, String userName, boolean multipleChoice) {
        return voteRepository.findByPollAndUser(pollId, userId)
                .collectList()
                .flatMap(existingVotes -> {
                    // 检查是否已投过这个选项
                    boolean alreadyVoted = existingVotes.stream()
                            .anyMatch(v -> v.getOptionId().equals(optionId));
                    if (alreadyVoted) {
                        return Mono.just(false); // 已投过
                    }

                    // 如果不允许多选，先删除之前的投票
                    Mono<Void> deleteOld = multipleChoice ? Mono.empty() :
                            voteRepository.deleteByPollAndUser(pollId, userId);

                    return deleteOld.then(voteRepository.save(PollVote.builder()
                            .pollId(pollId)
                            .optionId(optionId)
                            .userId(userId)
                            .userName(userName)
                            .createdAt(LocalDateTime.now())
                            .build())).thenReturn(true);
                });
    }

    /**
     * 定时检查并结束投票
     */
    @Scheduled(fixedRate = 10000)
    public void checkAndEndPolls() {
        pollRepository.findExpiredPolls(LocalDateTime.now())
                .flatMap(this::endPoll)
                .subscribe();
    }

    private Mono<Void> endPoll(Poll poll) {
        return optionRepository.findByPollId(poll.getId())
                .flatMap(option -> voteRepository.countByOptionId(option.getId())
                        .map(count -> new OptionResult(option, count)))
                .collectList()
                .flatMap(results -> {
                    pollRepository.updateStatus(poll.getId(), Poll.STATUS_ENDED).subscribe();
                    announceResults(poll, results);
                    return Mono.empty();
                });
    }

    private void announceResults(Poll poll, List<OptionResult> results) {
        DiscordBotService botService = applicationContext.getBean(DiscordBotService.class);
        if (!botService.isReady()) {
            log.error("Discord bot 未就绪");
            return;
        }

        TextChannel channel = botService.getJda().getTextChannelById(poll.getChannelId());
        if (channel == null) {
            log.error("找不到频道: {}", poll.getChannelId());
            return;
        }

        long totalVotes = results.stream().mapToLong(r -> r.count).sum();

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("📊 投票结束：" + poll.getTitle())
                .setColor(Color.GREEN);

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < results.size(); i++) {
            OptionResult r = results.get(i);
            double percent = totalVotes > 0 ? (r.count * 100.0 / totalVotes) : 0;
            String bar = generateProgressBar(percent);
            sb.append(EMOJI_NUMBERS[i]).append(" ").append(r.option.getContent())
                    .append("\n").append(bar).append(" ").append(r.count).append(" 票 (")
                    .append(String.format("%.1f", percent)).append("%)\n\n");
        }

        embed.setDescription(sb.toString());
        embed.setFooter("总投票数: " + totalVotes);

        channel.sendMessageEmbeds(embed.build()).queue();
    }

    private String generateProgressBar(double percent) {
        int filled = (int) (percent / 10);
        return "▓".repeat(filled) + "░".repeat(10 - filled);
    }

    public String getEmoji(int index) {
        return index < EMOJI_NUMBERS.length ? EMOJI_NUMBERS[index] : "🔢";
    }

    private record OptionResult(PollOption option, long count) {}
}
