package com.example.batchmonitor.domain;

import com.example.batchmonitor.service.BusinessCalendar;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.time.LocalDate;

@Getter
@RequiredArgsConstructor
public enum ScheduleRule {
    DAILY_BIZ("매영업일") {
        @Override
        public boolean isScheduled(LocalDate date, BusinessCalendar calendar) {
            return calendar.isBizDay(date);
        }
    },
    MONTHLY_FIRST_BIZ("매월 첫영업일") {
        @Override
        public boolean isScheduled(LocalDate date, BusinessCalendar calendar) {
            return calendar.isFirstBizDayOfMonth(date);
        }
    };

    private final String label;

    public abstract boolean isScheduled(LocalDate date, BusinessCalendar calendar);

    /**
     * BAT_PROC_LST.PROC_SCHD_PROD 한글 문구 → 규칙. 공백은 무시한다.
     * "매월 첫영업일"·"월초" → MONTHLY_FIRST_BIZ, "매영업일"·"영업일"·"매일"·"평일" → DAILY_BIZ, 그 외 null.
     */
    public static ScheduleRule fromText(String text) {
        if (text == null) {
            return null;
        }
        String t = text.replaceAll("\\s", "");
        if (t.equals("월초") || (t.contains("월") && t.contains("첫") && t.contains("영업일"))) {
            return MONTHLY_FIRST_BIZ;
        }
        if (t.contains("영업일") || t.equals("매일") || t.equals("평일")) {
            return DAILY_BIZ;
        }
        return null;
    }
}
