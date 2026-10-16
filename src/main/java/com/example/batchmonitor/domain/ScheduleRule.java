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
     * BAT_PROC_LST.PROC_SCHD_PROD → 규칙. 값은 label 과 같은 "매영업일", "매월 첫영업일" 두 가지이며
     * 공백 차이는 무시한다. 그 외 값은 null.
     */
    public static ScheduleRule fromText(String text) {
        if (text == null) {
            return null;
        }
        String t = text.replaceAll("\\s", "");
        for (ScheduleRule r : values()) {
            if (r.label.replaceAll("\\s", "").equals(t)) {
                return r;
            }
        }
        return null;
    }
}
