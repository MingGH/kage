package run.runnable.kage.listener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.Modal;
import org.springframework.stereotype.Component;
import run.runnable.kage.domain.doudizhu.DoudizhuGame;
import run.runnable.kage.domain.doudizhu.Player;
import run.runnable.kage.service.DoudizhuService;
import run.runnable.kage.service.EventDeduplicationService;

import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class DoudizhuButtonListener extends ListenerAdapter {

    private final DoudizhuService doudizhuService;
    private final EventDeduplicationService deduplicationService;

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String buttonId = event.getComponentId();
        
        if (!buttonId.startsWith("ddz_")) {
            return;
        }

        // 去重检查
        if (!deduplicationService.tryAcquire("button", event.getInteraction().getId())) {
            return;
        }

        String channelId = event.getChannel().getId();
        String userId = event.getUser().getId();
        String userName = event.getUser().getName();

        switch (buttonId) {
            case "ddz_join" -> handleJoin(event, channelId, userId, userName);
            case "ddz_cancel" -> handleCancel(event, channelId, userId);
            case "ddz_hand" -> handleViewHand(event, channelId, userId);
            case "ddz_play" -> handlePlayButton(event, channelId, userId);
            case "ddz_pass" -> handlePass(event, channelId, userId);
            case "ddz_bid_1" -> handleBid(event, channelId, userId, 1);
            case "ddz_bid_2" -> handleBid(event, channelId, userId, 2);
            case "ddz_bid_3" -> handleBid(event, channelId, userId, 3);
            case "ddz_bid_0" -> handleBid(event, channelId, userId, 0);
        }
    }

    private void handleJoin(ButtonInteractionEvent event, String channelId, String userId, String userName) {
        DoudizhuService.JoinResult result = doudizhuService.joinGame(channelId, userId, userName);
        
        switch (result) {
            case NO_GAME -> event.reply("❌ 当前没有进行中的游戏").setEphemeral(true).queue();
            case GAME_FULL -> event.reply("❌ 游戏已满").setEphemeral(true).queue();
            case ALREADY_IN_GAME -> event.reply("❌ 你已经在游戏中了").setEphemeral(true).queue();
            case GAME_STARTED -> {
                // 游戏开始，发送叫分界面
                DoudizhuGame game = doudizhuService.getGame(channelId);
                sendBiddingMessage(event, game);
                // 私发手牌给所有玩家
                sendHandsToPlayers(event, game);
            }
            case SUCCESS -> {
                DoudizhuGame game = doudizhuService.getGame(channelId);
                String playerList = game.getPlayers().stream()
                        .map(Player::getUserName)
                        .collect(Collectors.joining("\n- ", "- ", ""));
                
                String message = """
                        🎴 **斗地主**
                        
                        等待玩家加入 (%d/3)
                        %s
                        
                        点击下方按钮加入游戏！
                        """.formatted(game.getPlayers().size(), playerList);
                
                event.editMessage(message).queue();
            }
        }
    }

    private void handleCancel(ButtonInteractionEvent event, String channelId, String userId) {
        DoudizhuGame game = doudizhuService.getGame(channelId);
        if (game == null) {
            event.reply("❌ 没有进行中的游戏").setEphemeral(true).queue();
            return;
        }
        
        // 只有创建者或参与者可以取消
        if (game.getPlayer(userId) == null) {
            event.reply("❌ 你不是游戏参与者").setEphemeral(true).queue();
            return;
        }
        
        doudizhuService.endGame(channelId);
        event.editMessage("🎴 游戏已取消").setComponents().queue();
    }

    private void handleViewHand(ButtonInteractionEvent event, String channelId, String userId) {
        DoudizhuGame game = doudizhuService.getGame(channelId);
        if (game == null) {
            event.reply("❌ 没有进行中的游戏").setEphemeral(true).queue();
            return;
        }
        
        Player player = game.getPlayer(userId);
        if (player == null) {
            event.reply("❌ 你不是游戏参与者").setEphemeral(true).queue();
            return;
        }
        
        String hand = game.getHandDisplay(userId);
        String role = player.isLandlord() ? "👑 地主" : "🧑‍🌾 农民";
        event.reply("**你的手牌** (%s)\n%s".formatted(role, hand)).setEphemeral(true).queue();
    }

    private void handlePlayButton(ButtonInteractionEvent event, String channelId, String userId) {
        DoudizhuGame game = doudizhuService.getGame(channelId);
        if (game == null) {
            event.reply("❌ 没有进行中的游戏").setEphemeral(true).queue();
            return;
        }
        
        if (game.getCurrentPlayer() == null || !game.getCurrentPlayer().getUserId().equals(userId)) {
            event.reply("❌ 还没轮到你出牌").setEphemeral(true).queue();
            return;
        }
        
        // 弹出输入框让玩家输入要出的牌
        TextInput cardsInput = TextInput.create("cards", "输入要出的牌", TextInputStyle.SHORT)
                .setPlaceholder("例如: 334455 或 3 3 4 4 5 5 或 JQK")
                .setRequired(true)
                .build();
        
        Modal modal = Modal.create("ddz_play_modal", "出牌")
                .addComponents(ActionRow.of(cardsInput))
                .build();
        
        event.replyModal(modal).queue();
    }

    private void handlePass(ButtonInteractionEvent event, String channelId, String userId) {
        DoudizhuGame game = doudizhuService.getGame(channelId);
        if (game == null) {
            event.reply("❌ 没有进行中的游戏").setEphemeral(true).queue();
            return;
        }
        
        DoudizhuGame.PlayResult result = doudizhuService.pass(channelId, userId);
        
        switch (result) {
            case NOT_YOUR_TURN -> event.reply("❌ 还没轮到你").setEphemeral(true).queue();
            case MUST_PLAY -> event.reply("❌ 你必须出牌").setEphemeral(true).queue();
            case SUCCESS -> {
                event.deferEdit().queue();
                updateGameMessage(event, game);
            }
            default -> event.reply("❌ 操作失败").setEphemeral(true).queue();
        }
    }

    private void handleBid(ButtonInteractionEvent event, String channelId, String userId, int score) {
        DoudizhuGame.BidResult result = doudizhuService.bid(channelId, userId, score);
        
        switch (result) {
            case NOT_YOUR_TURN -> event.reply("❌ 还没轮到你叫分").setEphemeral(true).queue();
            case INVALID_SCORE -> event.reply("❌ 无效的分数").setEphemeral(true).queue();
            case SCORE_TOO_LOW -> event.reply("❌ 分数必须高于当前最高分").setEphemeral(true).queue();
            case CONTINUE -> {
                DoudizhuGame game = doudizhuService.getGame(channelId);
                String action = score == 0 ? "不叫" : "叫 " + score + " 分";
                event.reply("<@%s> %s".formatted(userId, action)).queue();
                sendBiddingButtons(event, game);
            }
            case LANDLORD_DECIDED -> {
                DoudizhuGame game = doudizhuService.getGame(channelId);
                String action = score == 0 ? "不叫" : "叫 " + score + " 分";
                event.reply("<@%s> %s".formatted(userId, action)).queue();
                
                // 发送地主确定消息和底牌
                sendLandlordDecidedMessage(event, game);
                // 更新地主手牌
                sendHandToPlayer(event, game.getPlayers().get(game.getLandlordIndex()));
                // 发送游戏界面
                sendPlayingMessage(event, game);
            }
            case NO_ONE_BID -> {
                event.reply("😅 没人叫分，重新发牌...").queue();
                DoudizhuGame game = doudizhuService.getGame(channelId);
                sendBiddingMessage(event, game);
                sendHandsToPlayers(event, game);
            }
            default -> event.reply("❌ 操作失败").setEphemeral(true).queue();
        }
    }

    @Override
    public void onModalInteraction(ModalInteractionEvent event) {
        if (!event.getModalId().equals("ddz_play_modal")) {
            return;
        }
        
        String channelId = event.getChannel().getId();
        String userId = event.getUser().getId();
        String cardsInput = event.getValue("cards").getAsString();
        
        DoudizhuGame.PlayResult result = doudizhuService.play(channelId, userId, cardsInput);
        DoudizhuGame game = doudizhuService.getGame(channelId);
        
        switch (result) {
            case NOT_YOUR_TURN -> event.reply("❌ 还没轮到你").setEphemeral(true).queue();
            case CARDS_NOT_FOUND -> event.reply("❌ 你没有这些牌，请检查输入").setEphemeral(true).queue();
            case INVALID_PATTERN -> event.reply("❌ 无效的牌型").setEphemeral(true).queue();
            case CANNOT_BEAT -> event.reply("❌ 压不过上家的牌").setEphemeral(true).queue();
            case WIN -> {
                event.reply("🎉 出牌成功！").setEphemeral(true).queue();
                sendGameOverMessage(event, game);
                doudizhuService.endGame(channelId);
            }
            case SUCCESS -> {
                event.reply("✅ 出牌成功！").setEphemeral(true).queue();
                updateGameMessage(event, game);
            }
            default -> event.reply("❌ 操作失败").setEphemeral(true).queue();
        }
    }

    // ========== 辅助方法 ==========

    private void sendBiddingMessage(ButtonInteractionEvent event, DoudizhuGame game) {
        Player currentBidder = game.getPlayers().get(game.getCurrentPlayerIndex());
        
        String message = """
                🎴 **叫分阶段**
                
                底牌已发，请叫分！
                当前轮到: <@%s>
                """.formatted(currentBidder.getUserId());
        
        event.getChannel().sendMessage(message)
                .addActionRow(
                        Button.primary("ddz_bid_1", "1分"),
                        Button.primary("ddz_bid_2", "2分"),
                        Button.primary("ddz_bid_3", "3分"),
                        Button.secondary("ddz_bid_0", "不叫")
                )
                .queue();
    }

    private void sendBiddingButtons(ButtonInteractionEvent event, DoudizhuGame game) {
        Player currentBidder = game.getPlayers().get(game.getCurrentPlayerIndex());
        int highestBid = game.getHighestBid();
        
        String message = "轮到 <@%s> 叫分".formatted(currentBidder.getUserId());
        
        // 根据当前最高分禁用按钮
        event.getChannel().sendMessage(message)
                .addActionRow(
                        Button.primary("ddz_bid_1", "1分").withDisabled(highestBid >= 1),
                        Button.primary("ddz_bid_2", "2分").withDisabled(highestBid >= 2),
                        Button.primary("ddz_bid_3", "3分").withDisabled(highestBid >= 3),
                        Button.secondary("ddz_bid_0", "不叫")
                )
                .queue();
    }

    private void sendLandlordDecidedMessage(ButtonInteractionEvent event, DoudizhuGame game) {
        Player landlord = game.getPlayers().get(game.getLandlordIndex());
        String landlordCards = game.getLandlordCards().stream()
                .map(c -> c.toString())
                .collect(Collectors.joining(" "));
        
        String message = """
                👑 **地主确定！**
                
                地主: <@%s>
                倍数: %dx
                
                底牌: %s
                """.formatted(landlord.getUserId(), game.getMultiplier(), landlordCards);
        
        event.getChannel().sendMessage(message).queue();
    }

    private void sendPlayingMessage(ButtonInteractionEvent event, DoudizhuGame game) {
        String status = game.getStatusDisplay();
        
        event.getChannel().sendMessage(status)
                .addActionRow(
                        Button.primary("ddz_play", "出牌").withEmoji(Emoji.fromUnicode("🃏")),
                        Button.secondary("ddz_pass", "过").withEmoji(Emoji.fromUnicode("⏭️")),
                        Button.secondary("ddz_hand", "查看手牌").withEmoji(Emoji.fromUnicode("👀"))
                )
                .queue();
    }

    private void updateGameMessage(ButtonInteractionEvent event, DoudizhuGame game) {
        String status = game.getStatusDisplay();
        
        event.getChannel().sendMessage(status)
                .addActionRow(
                        Button.primary("ddz_play", "出牌").withEmoji(Emoji.fromUnicode("🃏")),
                        Button.secondary("ddz_pass", "过").withEmoji(Emoji.fromUnicode("⏭️")),
                        Button.secondary("ddz_hand", "查看手牌").withEmoji(Emoji.fromUnicode("👀"))
                )
                .queue();
    }

    private void updateGameMessage(ModalInteractionEvent event, DoudizhuGame game) {
        String status = game.getStatusDisplay();
        
        event.getChannel().sendMessage(status)
                .addActionRow(
                        Button.primary("ddz_play", "出牌").withEmoji(Emoji.fromUnicode("🃏")),
                        Button.secondary("ddz_pass", "过").withEmoji(Emoji.fromUnicode("⏭️")),
                        Button.secondary("ddz_hand", "查看手牌").withEmoji(Emoji.fromUnicode("👀"))
                )
                .queue();
    }

    private void sendGameOverMessage(ModalInteractionEvent event, DoudizhuGame game) {
        String status = game.getStatusDisplay();
        event.getChannel().sendMessage(status).queue();
    }

    private void sendHandsToPlayers(ButtonInteractionEvent event, DoudizhuGame game) {
        for (Player player : game.getPlayers()) {
            sendHandToPlayer(event, player);
        }
    }

    private void sendHandToPlayer(ButtonInteractionEvent event, Player player) {
        String hand = player.getHand().stream()
                .map(c -> c.toString())
                .collect(Collectors.joining(" "));
        String role = player.isLandlord() ? "👑 地主" : "🧑‍🌾 农民";
        
        event.getJDA().retrieveUserById(player.getUserId()).queue(user -> {
            user.openPrivateChannel().queue(channel -> {
                channel.sendMessage("**你的手牌** (%s)\n%s".formatted(role, hand)).queue();
            }, error -> {
                log.warn("无法发送私信给用户 {}: {}", player.getUserId(), error.getMessage());
            });
        });
    }
}
