package run.runnable.kage.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import run.runnable.kage.domain.doudizhu.Card;
import run.runnable.kage.domain.doudizhu.DoudizhuGame;
import run.runnable.kage.domain.doudizhu.Player;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 斗地主游戏服务
 */
@Slf4j
@Service
public class DoudizhuService {
    
    // channelId -> game
    private final Map<String, DoudizhuGame> games = new ConcurrentHashMap<>();
    // oderId -> channelId (玩家当前所在游戏)
    private final Map<String, String> playerGames = new ConcurrentHashMap<>();
    
    /**
     * 创建新游戏
     */
    public DoudizhuGame createGame(String channelId, String creatorId, String creatorName) {
        // 检查频道是否已有游戏
        if (games.containsKey(channelId)) {
            return null;
        }
        
        String gameId = UUID.randomUUID().toString().substring(0, 8);
        DoudizhuGame game = new DoudizhuGame(gameId, channelId);
        game.join(creatorId, creatorName);
        
        games.put(channelId, game);
        playerGames.put(creatorId, channelId);
        
        log.info("创建斗地主游戏: {} 在频道 {}", gameId, channelId);
        return game;
    }
    
    /**
     * 加入游戏
     */
    public JoinResult joinGame(String channelId, String oderId, String userName) {
        DoudizhuGame game = games.get(channelId);
        if (game == null) {
            return JoinResult.NO_GAME;
        }
        
        if (game.getState() != DoudizhuGame.GameState.WAITING) {
            return JoinResult.GAME_STARTED;
        }
        
        if (playerGames.containsKey(oderId)) {
            return JoinResult.ALREADY_IN_GAME;
        }
        
        if (!game.join(oderId, userName)) {
            return JoinResult.GAME_FULL;
        }
        
        playerGames.put(oderId, channelId);
        
        // 检查是否可以开始
        if (game.canStart()) {
            game.start();
            return JoinResult.GAME_STARTED;
        }
        
        return JoinResult.SUCCESS;
    }
    
    /**
     * 叫分
     */
    public DoudizhuGame.BidResult bid(String channelId, String oderId, int score) {
        DoudizhuGame game = games.get(channelId);
        if (game == null) {
            return DoudizhuGame.BidResult.INVALID_STATE;
        }
        return game.bid(oderId, score);
    }
    
    /**
     * 出牌
     */
    public DoudizhuGame.PlayResult play(String channelId, String oderId, String cardsInput) {
        DoudizhuGame game = games.get(channelId);
        if (game == null) {
            return DoudizhuGame.PlayResult.INVALID_STATE;
        }
        
        Player player = game.getPlayer(oderId);
        if (player == null) {
            return DoudizhuGame.PlayResult.NOT_YOUR_TURN;
        }
        
        List<Card> cards = player.parseCards(cardsInput);
        if (cards == null || cards.isEmpty()) {
            return DoudizhuGame.PlayResult.CARDS_NOT_FOUND;
        }
        
        return game.play(oderId, cards);
    }
    
    /**
     * 过牌
     */
    public DoudizhuGame.PlayResult pass(String channelId, String oderId) {
        DoudizhuGame game = games.get(channelId);
        if (game == null) {
            return DoudizhuGame.PlayResult.INVALID_STATE;
        }
        return game.pass(oderId);
    }
    
    /**
     * 获取游戏
     */
    public DoudizhuGame getGame(String channelId) {
        return games.get(channelId);
    }
    
    /**
     * 获取玩家所在游戏
     */
    public DoudizhuGame getPlayerGame(String oderId) {
        String channelId = playerGames.get(oderId);
        if (channelId == null) return null;
        return games.get(channelId);
    }
    
    /**
     * 结束游戏
     */
    public void endGame(String channelId) {
        DoudizhuGame game = games.remove(channelId);
        if (game != null) {
            for (Player p : game.getPlayers()) {
                playerGames.remove(p.getUserId());
            }
            log.info("结束斗地主游戏: {} 在频道 {}", game.getGameId(), channelId);
        }
    }
    
    /**
     * 检查并清理超时游戏（超过30分钟）
     */
    public void cleanupStaleGames() {
        long now = System.currentTimeMillis();
        long timeout = 30 * 60 * 1000; // 30分钟
        
        games.entrySet().removeIf(entry -> {
            DoudizhuGame game = entry.getValue();
            if (now - game.getCreatedAt() > timeout) {
                for (Player p : game.getPlayers()) {
                    playerGames.remove(p.getUserId());
                }
                log.info("清理超时游戏: {}", game.getGameId());
                return true;
            }
            return false;
        });
    }
    
    public enum JoinResult {
        SUCCESS,
        NO_GAME,
        GAME_FULL,
        GAME_STARTED,
        ALREADY_IN_GAME
    }
}
