package run.runnable.kage.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public interface DateUtil {

    static String dateFormat(LocalDateTime localDateTime, String pattern){
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);
        return localDateTime.format(formatter);
    }
}
