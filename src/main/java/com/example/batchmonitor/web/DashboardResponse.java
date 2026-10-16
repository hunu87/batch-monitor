package com.example.batchmonitor.web;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Map;

@Getter
@Builder
public class DashboardResponse {
    private final String bizDate;
    private final boolean bizDay;
    private final String now;
    /** ALL + 상태별 건수 */
    private final Map<String, Integer> counts;
    /** 문제 우선 정렬 */
    private final List<JobRow> rows;
    private final List<IssueView> issues;

    @Getter
    @Builder
    public static class JobRow {
        private final String jobId;
        private final String name;
        private final String rule;
        private final String plan;
        private final int toleranceMin;
        private final int expectedMin;
        private final String owner;
        private final String runText;
        private final String status;
        private final String statusLabel;
        private final Integer processed;
        private final Long avg;
        private final String diffText;
        private final boolean diffAlert;
        private final String reason;
    }

    @Getter
    @Builder
    public static class IssueView {
        private final long id;
        private final String jobId;
        private final String time;
        private final String title;
        private final String desc;
        private final boolean acked;
    }

    @Getter
    @AllArgsConstructor
    public static class HistoryPoint {
        private final String date;
        private final int count;
    }
}
