package run.runnable.kage.domain.doudizhu;

import lombok.Getter;
import lombok.Setter;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 斗地主游戏核心逻辑
 */
@Getter
public class DoudizhuGame {
    
    private final String gameId;
    private final String channelId;
    private final long createdAt;
    
    // 玩家信息
    private final List<Player> players = new ArrayList<>(3);
    private int landlordIndex = -1; // 地主索引
    
    // 游戏状态
    @Setter
    private GameState state = GameState.WAITING;
    private int currentPlayerIndex = 0;
    private int currentBidder = 0; // 当前叫分的人
    private int highestBid = 0; // 最高叫分
    private int highestBidder = -1; // 最高叫分者
    private int passCount = 0; // 连续不叫的人数
    
    // 底牌
    private final List<Card> landlordCards = new ArrayList<>(3);
    
    // 当前出牌
    private CardPattern lastPattern = null;
    private int lastPlayerId = -1;
    private int consecutivePass = 0; // 连续过牌数
    
    // 倍数
    private int multiplier = 1;
    
    public DoudizhuGame(String gameId, String channelId) {
        this.gameId = gameId;
        this.channelId = channelId;
        this.createdAt = System.currentTimeMillis();
    }
    
    /**
     * 玩家加入
     */
    public boolean join(String oderId, String userName) {
        if (players.size() >= 3) return false;
        if (players.stream().anyMatch(p -> p.getUserId().equals(oderId))) return false;
        players.add(new Player(oderId, userName));
        return true;
    }
    
    /**
     * 检查是否可以开始
     */
    public boolean canStart() {
        return players.size() == 3 && state == GameState.WAITING;
    }
    
    /**
     * 开始游戏，发牌
     */
    public void start() {
        if (!canStart()) return;
        
        state = GameState.BIDDING;
        List<Card> deck = createDeck();
        Collections.shuffle(deck);
        
        // 发牌：每人17张，留3张底牌
        for (int i = 0; i < 51; i++) {
            players.get(i % 3).getHand().add(deck.get(i));
        }
        for (int i = 51; i < 54; i++) {
            landlordCards.add(deck.get(i));
        }
        
        // 排序手牌
        for (Player p : players) {
            p.sortHand();
        }
        
        // 随机选择第一个叫分的人
        currentBidder = new Random().nextInt(3);
        currentPlayerIndex = currentBidder;
    }
    
    /**
     * 叫分（1-3分，0表示不叫）
     */
    public BidResult bid(String oderId, int score) {
        if (state != GameState.BIDDING) {
            return BidResult.INVALID_STATE;
        }
        
        int playerIndex = getPlayerIndex(oderId);
        if (playerIndex != currentBidder) {
            return BidResult.NOT_YOUR_TURN;
        }
        
        if (score < 0 || score > 3) {
            return BidResult.INVALID_SCORE;
        }
        
        if (score > 0 && score <= highestBid) {
            return BidResult.SCORE_TOO_LOW;
        }
        
        if (score == 0) {
            // 不叫
            passCount++;
        } else {
            // 叫分
            highestBid = score;
            highestBidder = playerIndex;
            passCount = 0;
        }
        
        // 检查叫分是否结束
        if (score == 3 || passCount >= 3 || (highestBidder >= 0 && allBidded())) {
            if (highestBidder < 0) {
                // 没人叫，重新发牌
                resetForNewDeal();
                return BidResult.NO_ONE_BID;
            }
            // 确定地主
            finalizeLandlord();
            return BidResult.LANDLORD_DECIDED;
        }
        
        // 下一个人叫分
        currentBidder = (currentBidder + 1) % 3;
        return BidResult.CONTINUE;
    }
    
    private boolean allBidded() {
        // 简化：每人最多叫一次，三人都叫过了
        return passCount + (highestBidder >= 0 ? 1 : 0) >= 3;
    }
    
    private void finalizeLandlord() {
        landlordIndex = highestBidder;
        multiplier = highestBid;
        
        // 地主获得底牌
        Player landlord = players.get(landlordIndex);
        landlord.getHand().addAll(landlordCards);
        landlord.sortHand();
        
        // 设置角色
        for (int i = 0; i < 3; i++) {
            players.get(i).setLandlord(i == landlordIndex);
        }
        
        // 地主先出牌
        currentPlayerIndex = landlordIndex;
        state = GameState.PLAYING;
    }
    
    private void resetForNewDeal() {
        // 重置状态，重新发牌
        for (Player p : players) {
            p.getHand().clear();
        }
        landlordCards.clear();
        highestBid = 0;
        highestBidder = -1;
        passCount = 0;
        start();
    }
    
    /**
     * 出牌
     */
    public PlayResult play(String oderId, List<Card> cards) {
        if (state != GameState.PLAYING) {
            return PlayResult.INVALID_STATE;
        }
        
        int playerIndex = getPlayerIndex(oderId);
        if (playerIndex != currentPlayerIndex) {
            return PlayResult.NOT_YOUR_TURN;
        }
        
        Player player = players.get(playerIndex);
        
        // 检查是否有这些牌
        if (!player.hasCards(cards)) {
            return PlayResult.CARDS_NOT_FOUND;
        }
        
        // 解析牌型
        CardPattern pattern = CardPattern.parse(cards);
        if (pattern == null) {
            return PlayResult.INVALID_PATTERN;
        }
        
        // 检查是否能压过上家
        if (lastPattern != null && lastPlayerId != playerIndex) {
            if (!pattern.canBeat(lastPattern)) {
                return PlayResult.CANNOT_BEAT;
            }
        }
        
        // 出牌
        player.removeCards(cards);
        lastPattern = pattern;
        lastPlayerId = playerIndex;
        consecutivePass = 0;
        
        // 炸弹/火箭翻倍
        if (pattern.getType() == CardPattern.PatternType.BOMB ||
            pattern.getType() == CardPattern.PatternType.ROCKET) {
            multiplier *= 2;
        }
        
        // 检查是否获胜
        if (player.getHand().isEmpty()) {
            state = GameState.FINISHED;
            return PlayResult.WIN;
        }
        
        // 下一个玩家
        currentPlayerIndex = (currentPlayerIndex + 1) % 3;
        return PlayResult.SUCCESS;
    }
    
    /**
     * 过牌
     */
    public PlayResult pass(String oderId) {
        if (state != GameState.PLAYING) {
            return PlayResult.INVALID_STATE;
        }
        
        int playerIndex = getPlayerIndex(oderId);
        if (playerIndex != currentPlayerIndex) {
            return PlayResult.NOT_YOUR_TURN;
        }
        
        // 必须出牌的情况（新一轮或自己是上家）
        if (lastPattern == null || lastPlayerId == playerIndex) {
            return PlayResult.MUST_PLAY;
        }
        
        consecutivePass++;
        currentPlayerIndex = (currentPlayerIndex + 1) % 3;
        
        // 两人都过，新一轮
        if (consecutivePass >= 2) {
            lastPattern = null;
            consecutivePass = 0;
        }
        
        return PlayResult.SUCCESS;
    }
    
    /**
     * 获取玩家手牌显示
     */
    public String getHandDisplay(String oderId) {
        Player player = getPlayer(oderId);
        if (player == null) return "";
        return player.getHand().stream()
                .map(Card::toString)
                .collect(Collectors.joining(" "));
    }
    
    /**
     * 获取游戏状态显示
     */
    public String getStatusDisplay() {
        StringBuilder sb = new StringBuilder();
        sb.append("🎴 **斗地主**\n\n");
        
        if (state == GameState.WAITING) {
            sb.append("等待玩家加入 (").append(players.size()).append("/3)\n");
            for (Player p : players) {
                sb.append("- ").append(p.getUserName()).append("\n");
            }
        } else if (state == GameState.BIDDING) {
            sb.append("叫分阶段\n");
            sb.append("当前叫分: ").append(players.get(currentBidder).getUserName()).append("\n");
            if (highestBid > 0) {
                sb.append("最高叫分: ").append(highestBid).append("分 (")
                  .append(players.get(highestBidder).getUserName()).append(")\n");
            }
        } else if (state == GameState.PLAYING) {
            sb.append("地主: ").append(players.get(landlordIndex).getUserName()).append("\n");
            sb.append("倍数: ").append(multiplier).append("x\n\n");
            sb.append("轮到: ").append(players.get(currentPlayerIndex).getUserName()).append("\n");
            if (lastPattern != null) {
                sb.append("上家出牌: ").append(lastPattern.getType().getDisplayName()).append(" ");
                sb.append(lastPattern.getCards().stream().map(Card::toString).collect(Collectors.joining(" ")));
            } else {
                sb.append("新一轮，请出牌");
            }
            sb.append("\n\n剩余手牌:\n");
            for (Player p : players) {
                String role = p.isLandlord() ? "👑" : "🧑‍🌾";
                sb.append(role).append(" ").append(p.getUserName())
                  .append(": ").append(p.getHand().size()).append("张\n");
            }
        } else if (state == GameState.FINISHED) {
            Player winner = players.stream().filter(p -> p.getHand().isEmpty()).findFirst().orElse(null);
            if (winner != null) {
                String team = winner.isLandlord() ? "地主" : "农民";
                sb.append("🎉 **").append(team).append("获胜！**\n");
                sb.append("获胜者: ").append(winner.getUserName()).append("\n");
                sb.append("倍数: ").append(multiplier).append("x");
            }
        }
        
        return sb.toString();
    }
    
    // ========== 辅助方法 ==========
    
    private List<Card> createDeck() {
        List<Card> deck = new ArrayList<>(54);
        Card.Suit[] suits = {Card.Suit.SPADE, Card.Suit.HEART, Card.Suit.CLUB, Card.Suit.DIAMOND};
        Card.Rank[] ranks = {Card.Rank.THREE, Card.Rank.FOUR, Card.Rank.FIVE, Card.Rank.SIX,
                Card.Rank.SEVEN, Card.Rank.EIGHT, Card.Rank.NINE, Card.Rank.TEN,
                Card.Rank.JACK, Card.Rank.QUEEN, Card.Rank.KING, Card.Rank.ACE, Card.Rank.TWO};
        
        for (Card.Suit suit : suits) {
            for (Card.Rank rank : ranks) {
                deck.add(new Card(suit, rank));
            }
        }
        deck.add(new Card(Card.Suit.JOKER, Card.Rank.SMALL_JOKER));
        deck.add(new Card(Card.Suit.JOKER, Card.Rank.BIG_JOKER));
        return deck;
    }
    
    private int getPlayerIndex(String oderId) {
        for (int i = 0; i < players.size(); i++) {
            if (players.get(i).getUserId().equals(oderId)) return i;
        }
        return -1;
    }
    
    public Player getPlayer(String oderId) {
        return players.stream()
                .filter(p -> p.getUserId().equals(oderId))
                .findFirst().orElse(null);
    }
    
    public Player getCurrentPlayer() {
        if (currentPlayerIndex >= 0 && currentPlayerIndex < players.size()) {
            return players.get(currentPlayerIndex);
        }
        return null;
    }
    
    public List<Card> getLandlordCards() {
        return Collections.unmodifiableList(landlordCards);
    }
    
    /**
     * 游戏状态
     */
    public enum GameState {
        WAITING,    // 等待玩家
        BIDDING,    // 叫分阶段
        PLAYING,    // 游戏进行中
        FINISHED    // 游戏结束
    }
    
    /**
     * 叫分结果
     */
    public enum BidResult {
        CONTINUE,           // 继续叫分
        LANDLORD_DECIDED,   // 地主已确定
        NO_ONE_BID,         // 没人叫，重新发牌
        NOT_YOUR_TURN,      // 不是你的回合
        INVALID_SCORE,      // 无效分数
        SCORE_TOO_LOW,      // 分数太低
        INVALID_STATE       // 状态错误
    }
    
    /**
     * 出牌结果
     */
    public enum PlayResult {
        SUCCESS,        // 成功
        WIN,            // 获胜
        NOT_YOUR_TURN,  // 不是你的回合
        CARDS_NOT_FOUND,// 没有这些牌
        INVALID_PATTERN,// 无效牌型
        CANNOT_BEAT,    // 压不过
        MUST_PLAY,      // 必须出牌
        INVALID_STATE   // 状态错误
    }
}
