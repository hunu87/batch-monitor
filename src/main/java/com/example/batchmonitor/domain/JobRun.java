package com.example.batchmonitor.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class JobRun {
    private final long runId;
    private final String jobId;
    private final LocalDate bizDate;
    private final RunStatus status;
    private final LocalDateTime startedAt;
    private final LocalDateTime endedAt;
    private final Integer targetCount;
    private final int successCount;
    private final int failCount;
    private final String errorMsg;

    public int processed() {
        return successCount + failCount;
    }
}
