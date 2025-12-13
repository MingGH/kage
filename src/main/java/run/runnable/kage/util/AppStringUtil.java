package run.runnable.kage.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 字符串处理 工具类
 *
 * @author asher
 * @date 2025/07/12
 */
public interface AppStringUtil {

    static String convertUnicodeToChinese(String inputStr) {
        StringBuilder sb = new StringBuilder();
        String[] parts = inputStr.split("\\\\u");

        // 处理每一部分
        sb.append(parts[0]); // 添加第一个部分
        for (int i = 1; i < parts.length; i++) {
            if (parts[i].length() >= 4) {
                // 取出 Unicode 字符
                String unicode = parts[i].substring(0, 4);
                sb.append((char) Integer.parseInt(unicode, 16)); // 转换为字符
                sb.append(parts[i].substring(4)); // 添加剩余部分
            } else {
                sb.append(parts[i]); // 如果不足四位，直接添加
            }
        }
        return sb.toString();
    }

    /**
     * 使用指定分隔符连接多个字符串
     *
     * @param sep 分隔符
     * @param str 要连接的字符串数组
     * @return 连接后的字符串，如果输入为空则返回空字符串
     */
    static String joinSep(String sep, String... str){
        // 将字符串数组转换为流，使用reduce方法逐步累加
        return Arrays.stream(str)
                .reduce((acc, item) -> {
                    // 累加器函数：将累积值、分隔符和当前项连接起来
                    return acc + sep + item;
                })
                // 如果流为空（没有元素），则返回默认空字符串
                .orElse("");
    }



    /**
     * 提取书名
     *
     * @param input 输入
     * @return {@link List}<{@link String}>
     */
    static List<String> extractBookTitles(String input) {
        // 定义匹配书名的正则表达式，《书名》格式
        String regex = "《(.*?)》";
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(input);

        // 存储所有匹配到的书名
        List<String> bookTitles = new ArrayList<>();

        // 查找并提取所有符合正则表达式的书名
        while (matcher.find()) {
            bookTitles.add(matcher.group(1));  // group(1) 获取括号中的内容
        }

        return bookTitles;
    }
}
