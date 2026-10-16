package com.example.batchmonitor.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ScheduleRuleTest {

    @Test
    void 매영업일과_매월_첫영업일을_해석() {
        assertThat(ScheduleRule.fromText("매영업일")).isEqualTo(ScheduleRule.DAILY_BIZ);
        assertThat(ScheduleRule.fromText("매월 첫영업일")).isEqualTo(ScheduleRule.MONTHLY_FIRST_BIZ);
    }

    @Test
    void 공백_차이는_무시() {
        assertThat(ScheduleRule.fromText(" 매영업일 ")).isEqualTo(ScheduleRule.DAILY_BIZ);
        assertThat(ScheduleRule.fromText("매월첫영업일")).isEqualTo(ScheduleRule.MONTHLY_FIRST_BIZ);
    }

    @Test
    void 그_외_값이나_null은_null() {
        assertThat(ScheduleRule.fromText("매일")).isNull();
        assertThat(ScheduleRule.fromText("영업일")).isNull();
        assertThat(ScheduleRule.fromText(null)).isNull();
    }
}
