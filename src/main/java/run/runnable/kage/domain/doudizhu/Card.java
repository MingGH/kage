package run.runnable.kage.domain.doudizhu;

import lombok.Getter;

/**
 * 扑克牌
 */
@Getter
public class Card implements Comparable<Card> {
    
    private final Suit suit;
    private final Rank rank;
    
    public Card(Suit suit, Rank rank) {
        this.suit = suit;
        this.rank = rank;
    }
    
    /**
     * 获取牌的权重值（用于比较大小）
     */
    public int getWeight() {
        return rank.getWeight();
    }
    
    /**
     * 是否是大小王
     */
    public boolean isJoker() {
        return rank == Rank.SMALL_JOKER || rank == Rank.BIG_JOKER;
    }
    
    @Override
    public int compareTo(Card other) {
        return Integer.compare(this.getWeight(), other.getWeight());
    }
    
    @Override
    public String toString() {
        if (rank == Rank.SMALL_JOKER) return "🃏";
        if (rank == Rank.BIG_JOKER) return "👑";
        return suit.getEmoji() + rank.getDisplay();
    }
    
    /**
     * 简短显示（用于选牌）
     */
    public String toShortString() {
        if (rank == Rank.SMALL_JOKER) return "小王";
        if (rank == Rank.BIG_JOKER) return "大王";
        return rank.getDisplay();
    }
    
    /**
     * 花色
     */
    @Getter
    public enum Suit {
        SPADE("♠"),
        HEART("♥"),
        CLUB("♣"),
        DIAMOND("♦"),
        JOKER(""); // 王牌无花色
        
        private final String emoji;
        
        Suit(String emoji) {
            this.emoji = emoji;
        }
    }
    
    /**
     * 牌面值
     */
    @Getter
    public enum Rank {
        THREE("3", 3),
        FOUR("4", 4),
        FIVE("5", 5),
        SIX("6", 6),
        SEVEN("7", 7),
        EIGHT("8", 8),
        NINE("9", 9),
        TEN("10", 10),
        JACK("J", 11),
        QUEEN("Q", 12),
        KING("K", 13),
        ACE("A", 14),
        TWO("2", 15),
        SMALL_JOKER("小王", 16),
        BIG_JOKER("大王", 17);
        
        private final String display;
        private final int weight;
        
        Rank(String display, int weight) {
            this.display = display;
            this.weight = weight;
        }
        
        public static Rank fromDisplay(String display) {
            for (Rank r : values()) {
                if (r.display.equalsIgnoreCase(display)) {
                    return r;
                }
            }
            return null;
        }
    }
}
