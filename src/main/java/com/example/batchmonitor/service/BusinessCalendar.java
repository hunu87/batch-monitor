package com.example.batchmonitor.service;

import com.example.batchmonitor.repository.MonitorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/** 주말 + holiday 테이블 기준 영업일 계산 */
@Component
@RequiredArgsConstructor
public class BusinessCalendar {

    private final MonitorRepository repository;
    private volatile Set<LocalDate> holidays;

    /** 휴일 테이블을 바꾼 뒤 호출 */
    public void reload() {
        holidays = repository.findHolidays();
    }

    public boolean isBizDay(LocalDate date) {
        DayOfWeek dow = date.getDayOfWeek();
        return dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY && !holidays().contains(date);
    }

    public boolean isFirstBizDayOfMonth(LocalDate date) {
        if (!isBizDay(date)) {
            return false;
        }
        for (LocalDate d = date.withDayOfMonth(1); d.isBefore(date); d = d.plusDays(1)) {
            if (isBizDay(d)) {
                return false;
            }
        }
        return true;
    }

    /** date 이하의 가장 가까운 영업일 */
    public LocalDate latestBizDay(LocalDate date) {
        LocalDate d = date;
        while (!isBizDay(d)) {
            d = d.minusDays(1);
        }
        return d;
    }

    /** date 직전 영업일 n개 (오래된 순) */
    public List<LocalDate> bizDaysBefore(LocalDate date, int n) {
        List<LocalDate> days = new ArrayList<>();
        for (LocalDate d = date.minusDays(1); days.size() < n; d = d.minusDays(1)) {
            if (isBizDay(d)) {
                days.add(d);
            }
        }
        Collections.reverse(days);
        return days;
    }

    private Set<LocalDate> holidays() {
        if (holidays == null) {
            reload();
        }
        return holidays;
    }
}
