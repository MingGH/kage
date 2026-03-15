package run.runnable.kage.controller;

import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import run.runnable.kage.common.ApiResponse;
import run.runnable.kage.dto.LeaderboardEntry;
import run.runnable.kage.service.DiscordBotService;
import run.runnable.kage.service.LeaderboardStatsService;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/discord")
@RequiredArgsConstructor
public class DiscordController {

    private final DiscordBotService discordBotService;
    private final LeaderboardStatsService leaderboardStatsService;

    private static final int LEADERBOARD_LIMIT = 10;

    @GetMapping("/status")
    public Mono<ApiResponse<Map<String, Object>>> getStatus() {
        Map<String, Object> status = new HashMap<>();

        if (discordBotService.getJda() != null) {
            status.put("connected", discordBotService.isReady());
            status.put("botName", discordBotService.getJda().getSelfUser().getName());
            status.put("guildCount", discordBotService.getJda().getGuilds().size());
            status.put("status", discordBotService.getJda().getStatus().toString());
        } else {
            status.put("connected", false);
            status.put("message", "Discord bot not initialized");
        }

        return Mono.just(ApiResponse.success(status));
    }

    /**
     * 公开排行榜 API
     * GET /discord/leaderboard?period=day|week|month
     */
    @GetMapping("/leaderboard")
    public Mono<ApiResponse<List<LeaderboardEntry>>> getLeaderboard(
            @RequestParam(defaultValue = "day") String period) {

        String guildId = getFirstGuildId();
        if (guildId == null) {
            return Mono.just(ApiResponse.error(503, "Discord bot not connected"));
        }

        return switch (period) {
            case "week" -> leaderboardStatsService.getWeeklyLeaderboard(guildId, LEADERBOARD_LIMIT)
                    .collectList().map(list -> { enrichWithAvatars(list); return ApiResponse.success(list); });
            case "month" -> leaderboardStatsService.getMonthlyLeaderboard(guildId, LEADERBOARD_LIMIT)
                    .collectList().map(list -> { enrichWithAvatars(list); return ApiResponse.success(list); });
            default -> leaderboardStatsService.getDailyLeaderboard(guildId, LocalDate.now(), LEADERBOARD_LIMIT)
                    .collectList().map(list -> { enrichWithAvatars(list); return ApiResponse.success(list); });
        };
    }

    /**
     * 社区概况 API
     * GET /discord/stats
     */
    @GetMapping("/stats")
    public Mono<ApiResponse<Map<String, Object>>> getCommunityStats() {
        JDA jda = discordBotService.getJda();
        if (jda == null || !discordBotService.isReady()) {
            return Mono.just(ApiResponse.error(503, "Discord bot not connected"));
        }

        String guildId = getFirstGuildId();
        if (guildId == null) {
            return Mono.just(ApiResponse.error(503, "No guild found"));
        }

        Guild guild = jda.getGuildById(guildId);
        if (guild == null) {
            return Mono.just(ApiResponse.error(503, "Guild not found"));
        }

        // retrieveMetaData 从 Discord API 获取准确的成员总数，而非缓存数
        Mono<Integer> memberCountMono = Mono.fromFuture(
                guild.retrieveMetaData().submit()
        ).map(Guild.MetaData::getApproximateMembers);

        Mono<List<LeaderboardEntry>> topMono = leaderboardStatsService
                .getDailyLeaderboard(guildId, LocalDate.now(), 1)
                .collectList();

        return Mono.zip(memberCountMono, topMono)
                .map(tuple -> {
                    Map<String, Object> stats = new HashMap<>();
                    stats.put("guildName", guild.getName());
                    stats.put("memberCount", tuple.getT1());
                    List<LeaderboardEntry> topList = tuple.getT2();
                    if (!topList.isEmpty()) {
                        LeaderboardEntry top = topList.get(0);
                        stats.put("todayKing", top.getUserName());
                        stats.put("todayKingScore", top.getTotalScore());
                    }
                    return ApiResponse.success(stats);
                })
                .onErrorResume(e -> {
                    // fallback: 用缓存的 memberCount
                    Map<String, Object> stats = new HashMap<>();
                    stats.put("guildName", guild.getName());
                    stats.put("memberCount", guild.getMemberCount());
                    return leaderboardStatsService.getDailyLeaderboard(guildId, LocalDate.now(), 1)
                            .collectList()
                            .map(topList -> {
                                if (!topList.isEmpty()) {
                                    LeaderboardEntry top = topList.get(0);
                                    stats.put("todayKing", top.getUserName());
                                    stats.put("todayKingScore", top.getTotalScore());
                                }
                                return ApiResponse.success(stats);
                            });
                });
    }

    private String getFirstGuildId() {
        JDA jda = discordBotService.getJda();
        if (jda == null) return null;
        List<Guild> guilds = jda.getGuilds();
        return guilds.isEmpty() ? null : guilds.get(0).getId();
    }

    /**
     * 为排行榜条目填充 Discord 头像 URL
     */
    private void enrichWithAvatars(List<LeaderboardEntry> entries) {
        JDA jda = discordBotService.getJda();
        if (jda == null) return;

        String guildId = getFirstGuildId();
        if (guildId == null) return;
        Guild guild = jda.getGuildById(guildId);
        if (guild == null) return;

        for (LeaderboardEntry entry : entries) {
            try {
                Member member = guild.getMemberById(entry.getUserId());
                if (member != null) {
                    entry.setAvatarUrl(member.getEffectiveAvatarUrl() + "?size=64");
                }
            } catch (Exception ignored) {
                // 缓存中没有该成员，跳过
            }
        }
    }
}
