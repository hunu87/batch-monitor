package com.example.batchmonitor.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalTime;

/** BAT_PROC_LST 한 행 + 모니터 판정 기준 */
@Getter
@AllArgsConstructor
public class JobDef {
    /** PRGM_KND */
    private final String jobId;
    /** PRGM_NM */
    private final String name;
    /** PROC_SCHD_PROD 를 해석한 규칙 */
    private final ScheduleRule rule;
    /** PROC_SCHD_PROD 원문 (화면 표시용) */
    private final String ruleText;
    /** SCHD_TM */
    private final LocalTime planTime;
    /** 시작 허용 지연(분). 테이블에 없어서 monitor.default-tolerance-min 사용 */
    private final int toleranceMin;
    /** 예상 소요(분). 테이블에 없어서 monitor.default-expected-min 사용 */
    private final int expectedMin;
    /** MNGR */
    private final String owner;
}
