package run.runnable.kage.domain.doudizhu;

import lombok.Getter;
import lombok.Setter;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 斗地主玩家
 */
@Getter
public class Player {
    
    private final String oderId;
    private final String userName;
    private final List<Card> hand = new ArrayList<>();
    
    @Setter
    private boolean isLandlord = false;
    
    public Player(String oderId, String userName) {
        this.oderId = oderId;
        this.userName = userName;
    }
    
    public String getUserId() {
        return oderId;
    }
    
    /**
     * 排序手牌（从大到小）
     */
    public void sortHand() {
        hand.sort(Comparator.reverseOrder());
    }
    
    /**
     * 检查是否有这些牌
     */
    public boolean hasCards(List<Card> cards) {
        Map<String, Long> handCount = hand.stream()
                .collect(Collectors.groupingBy(this::cardKey, Collectors.counting()));
        Map<String, Long> needCount = cards.stream()
                .collect(Collectors.groupingBy(this::cardKey, Collectors.counting()));
        
        for (Map.Entry<String, Long> e : needCount.entrySet()) {
            if (handCount.getOrDefault(e.getKey(), 0L) < e.getValue()) {
                return false;
            }
        }
        return true;
    }
    
    /**
     * 移除出的牌
     */
    public void removeCards(List<Card> cards) {
        for (Card card : cards) {
            Iterator<Card> it = hand.iterator();
            while (it.hasNext()) {
                Card c = it.next();
                if (cardKey(c).equals(cardKey(card))) {
                    it.remove();
                    break;
                }
            }
        }
    }
    
    /**
     * 根据输入字符串解析要出的牌
     * 支持格式: "3 3 4 5 6" 或 "334456" 或 "JQK" 或 "小王 大王"
     */
    public List<Card> parseCards(String input) {
        if (input == null || input.isBlank()) return null;
        
        List<Card> result = new ArrayList<>();
        input = input.toUpperCase().trim();
        
        // 处理空格分隔
        String[] parts = input.split("\\s+");
        List<String> tokens = new ArrayList<>();
        
        for (String part : parts) {
            if (part.equals("小王") || part.equals("大王") || part.equals("10")) {
                tokens.add(part);
            } else {
                // 拆分连续字符 "JQK" -> ["J", "Q", "K"]
                for (char c : part.toCharArray()) {
                    tokens.add(String.valueOf(c));
                }
            }
        }
        
        // 解析每个 token
        for (String token : tokens) {
            Card.Rank rank = parseRank(token);
            if (rank == null) return null;
            
            // 从手牌中找一张匹配的
            Card found = findCardByRank(rank, result);
            if (found == null) return null;
            result.add(found);
        }
        
        return result;
    }
    
    private Card.Rank parseRank(String s) {
        return switch (s) {
            case "3" -> Card.Rank.THREE;
            case "4" -> Card.Rank.FOUR;
            case "5" -> Card.Rank.FIVE;
            case "6" -> Card.Rank.SIX;
            case "7" -> Card.Rank.SEVEN;
            case "8" -> Card.Rank.EIGHT;
            case "9" -> Card.Rank.NINE;
            case "10", "0" -> Card.Rank.TEN;
            case "J" -> Card.Rank.JACK;
            case "Q" -> Card.Rank.QUEEN;
            case "K" -> Card.Rank.KING;
            case "A", "1" -> Card.Rank.ACE;
            case "2" -> Card.Rank.TWO;
            case "小王" -> Card.Rank.SMALL_JOKER;
            case "大王" -> Card.Rank.BIG_JOKER;
            default -> null;
        };
    }
    
    /**
     * 从手牌中找一张指定 rank 的牌（排除已选的）
     */
    private Card findCardByRank(Card.Rank rank, List<Card> excluded) {
        Map<String, Long> excludedCount = excluded.stream()
                .collect(Collectors.groupingBy(this::cardKey, Collectors.counting()));
        
        for (Card c : hand) {
            if (c.getRank() == rank) {
                String key = cardKey(c);
                long used = excludedCount.getOrDefault(key, 0L);
                long total = hand.stream().filter(h -> cardKey(h).equals(key)).count();
                if (used < total) {
                    return c;
                }
            }
        }
        return null;
    }
    
    private String cardKey(Card c) {
        return c.getRank().name();
    }
}
