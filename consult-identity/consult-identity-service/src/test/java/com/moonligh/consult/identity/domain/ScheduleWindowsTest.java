package com.moonligh.consult.identity.domain;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import org.junit.jupiter.api.Test;

class ScheduleWindowsTest {

    private static final LocalDate DAY = LocalDate.of(2026, 10, 27);

    @Test
    void morningSlotIsLiveAtTen() {
        assertTrue(ScheduleWindows.live(DAY, LocalTime.of(8, 0), LocalTime.of(12, 0),
                LocalDateTime.of(DAY, LocalTime.of(10, 0))));
    }

    @Test
    void morningSlotIsDeadAtNight() {
        assertFalse(ScheduleWindows.live(DAY, LocalTime.of(8, 0), LocalTime.of(12, 0),
                LocalDateTime.of(DAY, LocalTime.of(22, 6))));
    }

    @Test
    void eveningSlotEndsAtTwentyOne() {
        assertFalse(ScheduleWindows.live(DAY, LocalTime.of(18, 0), LocalTime.of(21, 0),
                LocalDateTime.of(DAY, LocalTime.of(21, 0))));
        assertTrue(ScheduleWindows.live(DAY, LocalTime.of(18, 0), LocalTime.of(21, 0),
                LocalDateTime.of(DAY, LocalTime.of(20, 59))));
    }

    @Test
    void otherDayIsNeverLive() {
        assertFalse(ScheduleWindows.live(DAY.plusDays(1), LocalTime.of(8, 0), LocalTime.of(12, 0),
                LocalDateTime.of(DAY, LocalTime.of(10, 0))));
    }

    @Test
    void liveSqlUsesChinaClockNotServerLocalTime() {
        assertTrue(ScheduleWindows.LIVE_SQL.contains("UTC_TIMESTAMP()"));
        assertTrue(ScheduleWindows.LIVE_SQL.contains("INTERVAL 8 HOUR"));
        assertFalse(ScheduleWindows.LIVE_SQL.contains("CURRENT_TIME"));
        assertFalse(ScheduleWindows.LIVE_SQL.contains("CURDATE()"));
    }

    @Test
    void overnightWindowCoversLateNight() {
        assertTrue(ScheduleWindows.live(DAY, LocalTime.of(23, 0), LocalTime.of(8, 0),
                LocalDateTime.of(DAY, LocalTime.of(23, 30))));
        assertTrue(ScheduleWindows.live(DAY, LocalTime.of(23, 0), LocalTime.of(8, 0),
                LocalDateTime.of(DAY, LocalTime.of(1, 0))));
        assertFalse(ScheduleWindows.live(DAY, LocalTime.of(23, 0), LocalTime.of(8, 0),
                LocalDateTime.of(DAY, LocalTime.of(10, 0))));
    }
}
