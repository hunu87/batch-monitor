package com.example.batchmonitor.repository;

import com.example.batchmonitor.domain.Status;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class IssueRow {
    private final long issueId;
    private final String jobId;
    private final Status status;
    private final String message;
    private final LocalDateTime occurredAt;
    private final boolean acked;
}
