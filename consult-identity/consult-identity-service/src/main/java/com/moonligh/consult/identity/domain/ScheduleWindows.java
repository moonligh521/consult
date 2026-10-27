package com.moonligh.consult.identity.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 当前时段才算出诊。上午号源不能拿到晚上还占坑。
 */
public final class ScheduleWindows {

    /** 号源按东八区，不跟 MySQL 机器时区走。 */
    public static final String CLOCK_SQL = "DATE_ADD(UTC_TIMESTAMP(), INTERVAL 8 HOUR)";

    /**
     * 派单 SQL：只取今天、已经开始、还没结束的班。隔夜班（23:00-08:00）单独认。
     */
    public static final String LIVE_SQL =
            " AND s.visit_date = DATE(" + CLOCK_SQL + ")"
            + " AND ("
            + " (s.start_time <= s.end_time AND s.start_time <= TIME(" + CLOCK_SQL + ") AND s.end_time > TIME(" + CLOCK_SQL + "))"
            + " OR (s.start_time > s.end_time AND (s.start_time <= TIME(" + CLOCK_SQL + ") OR s.end_time > TIME(" + CLOCK_SQL + ")))"
            + " )";

    private ScheduleWindows() {
    }

    public static boolean live(LocalDate visitDate, LocalTime start, LocalTime end, LocalDateTime now) {
        if (visitDate == null || start == null || end == null || now == null) {
            return false;
        }
        if (!visitDate.equals(now.toLocalDate())) {
            return false;
        }
        LocalTime clock = now.toLocalTime();
        if (!start.isAfter(end)) {
            return !clock.isBefore(start) && clock.isBefore(end);
        }
        return !clock.isBefore(start) || clock.isBefore(end);
    }
}
