package run.runnable.kage.domain.doudizhu;

import lombok.Getter;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 牌型判断和比较
 */
@Getter
public class CardPattern {
    
    private final PatternType type;
    private final List<Card> cards;
    private final int mainWeight; // 主牌权重（用于比较）
    
    private CardPattern(PatternType type, List<Card> cards, int mainWeight) {
        this.type = type;
        this.cards = new ArrayList<>(cards);
        this.mainWeight = mainWeight;
    }
    
    /**
     * 解析牌型
     */
    public static CardPattern parse(List<Card> cards) {
        if (cards == null || cards.isEmpty()) {
            return null;
        }
        
        int size = cards.size();
        List<Card> sorted = cards.stream().sorted().collect(Collectors.toList());
        Map<Integer, List<Card>> weightGroups = groupByWeight(cards);
        
        // 火箭（双王）
        if (size == 2 && isRocket(cards)) {
            return new CardPattern(PatternType.ROCKET, sorted, 100);
        }
        
        // 炸弹
        if (size == 4 && weightGroups.size() == 1) {
            return new CardPattern(PatternType.BOMB, sorted, sorted.get(0).getWeight());
        }
        
        // 单张
        if (size == 1) {
            return new CardPattern(PatternType.SINGLE, sorted, sorted.get(0).getWeight());
        }
        
        // 对子
        if (size == 2 && weightGroups.size() == 1) {
            return new CardPattern(PatternType.PAIR, sorted, sorted.get(0).getWeight());
        }
        
        // 三张
        if (size == 3 && weightGroups.size() == 1) {
            return new CardPattern(PatternType.TRIPLE, sorted, sorted.get(0).getWeight());
        }
        
        // 三带一
        if (size == 4) {
            CardPattern p = parseTripleWithOne(weightGroups, sorted);
            if (p != null) return p;
        }
        
        // 三带二
        if (size == 5) {
            CardPattern p = parseTripleWithPair(weightGroups, sorted);
            if (p != null) return p;
        }
        
        // 顺子（至少5张）
        if (size >= 5) {
            CardPattern p = parseStraight(sorted);
            if (p != null) return p;
        }
        
        // 连对（至少3对）
        if (size >= 6 && size % 2 == 0) {
            CardPattern p = parsePairStraight(sorted, weightGroups);
            if (p != null) return p;
        }
        
        // 飞机不带
        if (size >= 6 && size % 3 == 0) {
            CardPattern p = parsePlane(sorted, weightGroups);
            if (p != null) return p;
        }
        
        // 飞机带单
        if (size >= 8 && size % 4 == 0) {
            CardPattern p = parsePlaneWithSingles(sorted, weightGroups);
            if (p != null) return p;
        }
        
        // 飞机带对
        if (size >= 10 && size % 5 == 0) {
            CardPattern p = parsePlaneWithPairs(sorted, weightGroups);
            if (p != null) return p;
        }
        
        // 四带二单
        if (size == 6) {
            CardPattern p = parseFourWithTwo(weightGroups, sorted);
            if (p != null) return p;
        }
        
        // 四带二对
        if (size == 8) {
            CardPattern p = parseFourWithTwoPairs(weightGroups, sorted);
            if (p != null) return p;
        }
        
        return null; // 无效牌型
    }
    
    /**
     * 判断是否能压过另一手牌
     */
    public boolean canBeat(CardPattern other) {
        if (other == null) return true;
        
        // 火箭最大
        if (this.type == PatternType.ROCKET) return true;
        if (other.type == PatternType.ROCKET) return false;
        
        // 炸弹能压非炸弹
        if (this.type == PatternType.BOMB && other.type != PatternType.BOMB) return true;
        if (other.type == PatternType.BOMB && this.type != PatternType.BOMB) return false;
        
        // 同类型比较
        if (this.type != other.type) return false;
        if (this.cards.size() != other.cards.size()) return false;
        
        return this.mainWeight > other.mainWeight;
    }
    
    // ========== 辅助方法 ==========
    
    private static Map<Integer, List<Card>> groupByWeight(List<Card> cards) {
        return cards.stream().collect(Collectors.groupingBy(Card::getWeight));
    }
    
    private static boolean isRocket(List<Card> cards) {
        return cards.stream().allMatch(Card::isJoker);
    }
    
    private static boolean isConsecutive(List<Integer> weights) {
        if (weights.size() < 2) return true;
        List<Integer> sorted = weights.stream().sorted().collect(Collectors.toList());
        for (int i = 1; i < sorted.size(); i++) {
            if (sorted.get(i) - sorted.get(i - 1) != 1) return false;
            if (sorted.get(i) > 14) return false; // 2和王不能连
        }
        return true;
    }
    
    private static CardPattern parseTripleWithOne(Map<Integer, List<Card>> groups, List<Card> sorted) {
        for (Map.Entry<Integer, List<Card>> e : groups.entrySet()) {
            if (e.getValue().size() == 3) {
                return new CardPattern(PatternType.TRIPLE_WITH_SINGLE, sorted, e.getKey());
            }
        }
        return null;
    }
    
    private static CardPattern parseTripleWithPair(Map<Integer, List<Card>> groups, List<Card> sorted) {
        int tripleWeight = -1;
        boolean hasPair = false;
        for (Map.Entry<Integer, List<Card>> e : groups.entrySet()) {
            if (e.getValue().size() == 3) tripleWeight = e.getKey();
            if (e.getValue().size() == 2) hasPair = true;
        }
        if (tripleWeight > 0 && hasPair) {
            return new CardPattern(PatternType.TRIPLE_WITH_PAIR, sorted, tripleWeight);
        }
        return null;
    }
    
    private static CardPattern parseStraight(List<Card> sorted) {
        List<Integer> weights = sorted.stream().map(Card::getWeight).collect(Collectors.toList());
        Set<Integer> unique = new HashSet<>(weights);
        if (unique.size() != sorted.size()) return null; // 有重复
        if (!isConsecutive(new ArrayList<>(unique))) return null;
        return new CardPattern(PatternType.STRAIGHT, sorted, Collections.min(weights));
    }
    
    private static CardPattern parsePairStraight(List<Card> sorted, Map<Integer, List<Card>> groups) {
        if (groups.values().stream().anyMatch(l -> l.size() != 2)) return null;
        List<Integer> weights = new ArrayList<>(groups.keySet());
        if (weights.size() < 3) return null;
        if (!isConsecutive(weights)) return null;
        return new CardPattern(PatternType.PAIR_STRAIGHT, sorted, Collections.min(weights));
    }
    
    private static CardPattern parsePlane(List<Card> sorted, Map<Integer, List<Card>> groups) {
        if (groups.values().stream().anyMatch(l -> l.size() != 3)) return null;
        List<Integer> weights = new ArrayList<>(groups.keySet());
        if (weights.size() < 2) return null;
        if (!isConsecutive(weights)) return null;
        return new CardPattern(PatternType.PLANE, sorted, Collections.min(weights));
    }
    
    private static CardPattern parsePlaneWithSingles(List<Card> sorted, Map<Integer, List<Card>> groups) {
        List<Integer> tripleWeights = new ArrayList<>();
        for (Map.Entry<Integer, List<Card>> e : groups.entrySet()) {
            if (e.getValue().size() >= 3) tripleWeights.add(e.getKey());
        }
        int planeCount = sorted.size() / 4;
        if (tripleWeights.size() < planeCount) return null;
        
        // 找连续的三张
        tripleWeights.sort(Integer::compareTo);
        for (int i = 0; i <= tripleWeights.size() - planeCount; i++) {
            List<Integer> sub = tripleWeights.subList(i, i + planeCount);
            if (isConsecutive(sub)) {
                return new CardPattern(PatternType.PLANE_WITH_SINGLES, sorted, sub.get(0));
            }
        }
        return null;
    }
    
    private static CardPattern parsePlaneWithPairs(List<Card> sorted, Map<Integer, List<Card>> groups) {
        List<Integer> tripleWeights = new ArrayList<>();
        int pairCount = 0;
        for (Map.Entry<Integer, List<Card>> e : groups.entrySet()) {
            if (e.getValue().size() >= 3) tripleWeights.add(e.getKey());
            if (e.getValue().size() == 2) pairCount++;
        }
        int planeCount = sorted.size() / 5;
        if (tripleWeights.size() < planeCount || pairCount < planeCount) return null;
        
        tripleWeights.sort(Integer::compareTo);
        for (int i = 0; i <= tripleWeights.size() - planeCount; i++) {
            List<Integer> sub = tripleWeights.subList(i, i + planeCount);
            if (isConsecutive(sub)) {
                return new CardPattern(PatternType.PLANE_WITH_PAIRS, sorted, sub.get(0));
            }
        }
        return null;
    }
    
    private static CardPattern parseFourWithTwo(Map<Integer, List<Card>> groups, List<Card> sorted) {
        for (Map.Entry<Integer, List<Card>> e : groups.entrySet()) {
            if (e.getValue().size() == 4) {
                return new CardPattern(PatternType.FOUR_WITH_TWO, sorted, e.getKey());
            }
        }
        return null;
    }
    
    private static CardPattern parseFourWithTwoPairs(Map<Integer, List<Card>> groups, List<Card> sorted) {
        int fourWeight = -1;
        int pairCount = 0;
        for (Map.Entry<Integer, List<Card>> e : groups.entrySet()) {
            if (e.getValue().size() == 4) fourWeight = e.getKey();
            if (e.getValue().size() == 2) pairCount++;
        }
        if (fourWeight > 0 && pairCount >= 2) {
            return new CardPattern(PatternType.FOUR_WITH_TWO_PAIRS, sorted, fourWeight);
        }
        return null;
    }
    
    /**
     * 牌型枚举
     */
    public enum PatternType {
        SINGLE("单张"),
        PAIR("对子"),
        TRIPLE("三张"),
        TRIPLE_WITH_SINGLE("三带一"),
        TRIPLE_WITH_PAIR("三带二"),
        STRAIGHT("顺子"),
        PAIR_STRAIGHT("连对"),
        PLANE("飞机"),
        PLANE_WITH_SINGLES("飞机带单"),
        PLANE_WITH_PAIRS("飞机带对"),
        FOUR_WITH_TWO("四带二"),
        FOUR_WITH_TWO_PAIRS("四带两对"),
        BOMB("炸弹"),
        ROCKET("火箭");
        
        @Getter
        private final String displayName;
        
        PatternType(String displayName) {
            this.displayName = displayName;
        }
    }
}
