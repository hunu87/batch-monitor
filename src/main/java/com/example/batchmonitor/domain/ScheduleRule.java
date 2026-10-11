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
}
