package run.runnable.kage.command.impl;

import org.springframework.stereotype.Component;
import run.runnable.kage.command.CommandContext;
import run.runnable.kage.command.UnifiedCommand;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Random;

/**
 * 今日运势/摸鱼指数命令
 */
@Component
public class FortuneCommand implements UnifiedCommand {

    private static final String[] FORTUNE_LEVELS = {
            "大吉", "中吉", "小吉", "吉", "末吉", "凶", "大凶"
    };

    private static final String[] MOYU_ACTIVITIES = {
            "刷短视频", "看小说", "逛淘宝", "聊微信", "发呆放空",
            "喝咖啡", "吃零食", "看新闻", "刷微博", "听音乐",
            "看直播", "玩手游", "逛论坛", "看B站", "摸鱼聊天"
    };

    private static final String[] WORK_ACTIVITIES = {
            "写代码", "开会", "写文档", "回邮件", "做PPT",
            "改Bug", "需求评审", "代码Review", "学习新技术", "整理桌面"
    };

    private static final String[] LUCKY_THINGS = {
            "绿色", "蓝色", "红色", "黄色", "紫色", "白色", "黑色",
            "咖啡", "奶茶", "可乐", "矿泉水", "果汁",
            "数字3", "数字7", "数字8", "数字9",
            "东方", "南方", "西方", "北方"
    };

    private static final String[] MOYU_TIPS = {
            "摸鱼一时爽，一直摸一直爽！",
            "今天也是元气满满摸鱼的一天呢~",
            "老板不在，摸鱼正当时！",
            "适度摸鱼，有益身心健康",
            "工作是为了更好地摸鱼",
            "摸鱼使我快乐，快乐使我高效",
            "今日宜摸鱼，不宜加班",
            "摸鱼虽好，可不要贪杯哦~",
            "偷得浮生半日闲，摸鱼摸到自然醒",
            "人生苦短，及时摸鱼"
    };

    @Override
    public String getName() {
        return "fortune";
    }

    @Override
    public String getDescription() {
        return "查看今日运势和摸鱼指数";
    }

    @Override
    public void execute(CommandContext ctx) {
        String userId = ctx.getUser().getId();
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
        
        // 使用用户ID + 日期作为种子，保证同一天同一用户结果一致
        long seed = (userId + today.toString()).hashCode();
        Random random = new Random(seed);

        // 生成各项运势
        int fortuneIndex = random.nextInt(FORTUNE_LEVELS.length);
        String fortune = FORTUNE_LEVELS[fortuneIndex];
        
        int moyuIndex = random.nextInt(100) + 1;  // 1-100
        String moyuLevel = getMoyuLevel(moyuIndex);
        
        String luckyThing = LUCKY_THINGS[random.nextInt(LUCKY_THINGS.length)];
        String goodActivity = MOYU_ACTIVITIES[random.nextInt(MOYU_ACTIVITIES.length)];
        String badActivity = WORK_ACTIVITIES[random.nextInt(WORK_ACTIVITIES.length)];
        String tip = MOYU_TIPS[random.nextInt(MOYU_TIPS.length)];

        // 构建运势卡片
        StringBuilder sb = new StringBuilder();
        sb.append("🔮 **今日运势** 🔮\n");
        sb.append("━━━━━━━━━━━━━━━\n\n");
        
        sb.append("📅 ").append(today).append("\n\n");
        
        sb.append("**整体运势**: ").append(getFortuneEmoji(fortuneIndex)).append(" ").append(fortune).append("\n\n");
        
        sb.append("**🐟 摸鱼指数**: ").append(moyuIndex).append("/100 ").append(moyuLevel).append("\n");
        sb.append(getMoyuBar(moyuIndex)).append("\n\n");
        
        sb.append("**🍀 幸运物**: ").append(luckyThing).append("\n");
        sb.append("**✅ 宜**: ").append(goodActivity).append("\n");
        sb.append("**❌ 忌**: ").append(badActivity).append("\n\n");
        
        sb.append("━━━━━━━━━━━━━━━\n");
        sb.append("💬 ").append(tip);

        ctx.reply(sb.toString());
    }

    private String getMoyuLevel(int index) {
        if (index >= 90) return "🌟 摸鱼大师";
        if (index >= 70) return "😎 摸鱼高手";
        if (index >= 50) return "🙂 摸鱼达人";
        if (index >= 30) return "😐 摸鱼新手";
        return "😢 今日不宜摸鱼";
    }

    private String getMoyuBar(int index) {
        int filled = index / 10;
        int empty = 10 - filled;
        return "▓".repeat(filled) + "░".repeat(empty);
    }

    private String getFortuneEmoji(int index) {
        return switch (index) {
            case 0 -> "🌟";  // 大吉
            case 1 -> "✨";  // 中吉
            case 2 -> "⭐";  // 小吉
            case 3 -> "🌙";  // 吉
            case 4 -> "☁️";  // 末吉
            case 5 -> "🌧️";  // 凶
            case 6 -> "⛈️";  // 大凶
            default -> "🔮";
        };
    }
}
