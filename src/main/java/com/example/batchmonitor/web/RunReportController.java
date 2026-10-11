package com.example.batchmonitor.web;

import com.example.batchmonitor.domain.JobRun;
import com.example.batchmonitor.domain.RunStatus;
import com.example.batchmonitor.repository.MonitorRepository;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;

/**
 * 각 발송 배치가 호출하는 API.
 * 시작 → (선택) 진행 건수 → 종료 순으로 보고한다.
 */
@RestController
@RequestMapping("/api/runs")
@RequiredArgsConstructor
public class RunReportController {

    private final MonitorRepository repository;
    private final Clock clock;

    @PostMapping
    public Map<String, Long> start(@RequestBody StartRequest req) {
        repository.findJob(req.getJobId())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "unknown job " + req.getJobId()));
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDate bizDate = req.getBizDate() != null ? req.getBizDate() : now.toLocalDate();
        long runId = repository.insertRun(req.getJobId(), bizDate, now, req.getTargetCount());
        return Map.of("runId", runId);
    }

    @PutMapping("/{runId}/progress")
    public void progress(@PathVariable long runId, @RequestBody CountRequest req) {
        requireRunning(runId);
        repository.updateProgress(runId, req.getSuccessCount(), req.getFailCount());
    }

    @PutMapping("/{runId}/finish")
    public void finish(@PathVariable long runId, @RequestBody FinishRequest req) {
        if (req.getStatus() == null || req.getStatus() == RunStatus.RUNNING) {
            throw new ResponseStatusException(BAD_REQUEST, "status must be SUCCESS or FAILED");
        }
        requireRunning(runId);
        repository.finishRun(runId, req.getStatus(), LocalDateTime.now(clock),
                req.getSuccessCount(), req.getFailCount(), req.getErrorMsg());
    }

    private void requireRunning(long runId) {
        JobRun run = repository.findRun(runId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "unknown run " + runId));
        if (run.getStatus() != RunStatus.RUNNING) {
            throw new ResponseStatusException(CONFLICT, "run " + runId + " already finished");
        }
    }

    @Getter
    @Setter
    public static class StartRequest {
        private String jobId;
        /** 생략 시 오늘 */
        private LocalDate bizDate;
        private Integer targetCount;
    }

    @Getter
    @Setter
    public static class CountRequest {
        private int successCount;
        private int failCount;
    }

    @Getter
    @Setter
    public static class FinishRequest extends CountRequest {
        private RunStatus status;
        private String errorMsg;
    }
}
