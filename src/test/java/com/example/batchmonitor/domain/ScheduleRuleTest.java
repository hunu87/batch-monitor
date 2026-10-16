package com.example.batchmonitor.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ScheduleRuleTest {

    @Test
    void 영업일_문구는_매영업일() {
        assertThat(ScheduleRule.fromText("매영업일")).isEqualTo(ScheduleRule.DAILY_BIZ);
        assertThat(ScheduleRule.fromText("영업일")).isEqualTo(ScheduleRule.DAILY_BIZ);
        assertThat(ScheduleRule.fromText("매일")).isEqualTo(ScheduleRule.DAILY_BIZ);
        assertThat(ScheduleRule.fromText(" 평일 ")).isEqualTo(ScheduleRule.DAILY_BIZ);
    }

    @Test
    void 월_첫영업일_문구는_매월_첫영업일() {
        assertThat(ScheduleRule.fromText("매월 첫영업일")).isEqualTo(ScheduleRule.MONTHLY_FIRST_BIZ);
        assertThat(ScheduleRule.fromText("매월첫영업일")).isEqualTo(ScheduleRule.MONTHLY_FIRST_BIZ);
        assertThat(ScheduleRule.fromText("월초")).isEqualTo(ScheduleRule.MONTHLY_FIRST_BIZ);
    }

    @Test
    void 모르는_문구나_null은_null() {
        assertThat(ScheduleRule.fromText("매주 월")).isNull();
        assertThat(ScheduleRule.fromText(null)).isNull();
    }
}
