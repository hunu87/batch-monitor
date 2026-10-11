package com.example.batchmonitor.web;

import com.example.batchmonitor.repository.MonitorRepository;
import com.example.batchmonitor.service.BusinessCalendar;
import com.example.batchmonitor.service.MonitorService;
import com.example.batchmonitor.web.DashboardResponse.HistoryPoint;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class DashboardController {

    private final MonitorService monitorService;
    private final BusinessCalendar calendar;
    private final MonitorRepository repository;

    /** date 생략 시 오늘(휴일이면 직전 영업일) */
    @GetMapping("/dashboard")
    public DashboardResponse dashboard(@RequestParam(required = false)
                                       @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return monitorService.dashboard(resolve(date));
    }

    @GetMapping("/jobs/{jobId}/history")
    public List<HistoryPoint> history(@PathVariable String jobId,
                                      @RequestParam(required = false)
                                      @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return monitorService.history(jobId, resolve(date));
    }

    @PostMapping("/issues/{issueId}/ack")
    public void ack(@PathVariable long issueId, @RequestBody AckRequest req) {
        if (repository.ackIssue(issueId, req.isAcked()) == 0) {
            throw new ResponseStatusException(NOT_FOUND, "unknown issue " + issueId);
        }
    }

    private LocalDate resolve(LocalDate date) {
        return date != null ? date : calendar.latestBizDay(monitorService.today());
    }

    @Getter
    @Setter
    public static class AckRequest {
        private boolean acked = true;
    }
}
