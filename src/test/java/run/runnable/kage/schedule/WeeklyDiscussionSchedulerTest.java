package run.runnable.kage.schedule;

import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WeeklyDiscussionSchedulerTest {

    @Test
    void threadName_usesMondayThroughFridayRange() {
        assertThat(WeeklyDiscussionScheduler.threadName(LocalDate.of(2026, 10, 28)))
                .isEqualTo("本周摸鱼局 · 10.26–10.30");
    }

    @Test
    void prompts_matchWeeklyRhythm() {
        LocalDate date = LocalDate.of(2026, 10, 26);

        assertThat(WeeklyDiscussionScheduler.prompt(DayOfWeek.MONDAY, date))
                .contains("这周准备怎么摸", "周三中午");
        assertThat(WeeklyDiscussionScheduler.prompt(DayOfWeek.WEDNESDAY, date))
                .contains("摸鱼工具", "带上链接");
        assertThat(WeeklyDiscussionScheduler.prompt(DayOfWeek.FRIDAY, date))
                .contains("吐槽", "周末愉快");
    }

    @Test
    void prompt_rejectsUnsupportedDay() {
        assertThatThrownBy(() ->
                WeeklyDiscussionScheduler.prompt(DayOfWeek.TUESDAY, LocalDate.of(2026, 10, 26)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
