package run.runnable.kage.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import run.runnable.kage.common.ApiResponse;
import run.runnable.kage.service.DiscordBotService;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/discord")
@RequiredArgsConstructor
public class DiscordController {

    private final DiscordBotService discordBotService;

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
}