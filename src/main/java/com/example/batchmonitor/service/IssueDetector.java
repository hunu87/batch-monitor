package com.example.batchmonitor.service;

import com.example.batchmonitor.domain.Status;
import com.example.batchmonitor.repository.MonitorRepository;
import com.example.batchmonitor.service.MonitorService.JobSnapshot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * 주기적으로 오늘 상태를 계산해서 실패/미실행/지연/확인필요를 이슈로 남긴다.
 * "미실행"은 배치가 아무것도 보고하지 않아야 생기는 상태라, 이렇게 서버 쪽에서 시간을 보고 판정해야 잡힌다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IssueDetector {

    private final MonitorService monitorService;
    private final MonitorRepository repository;
    private final BusinessCalendar calendar;

    @Scheduled(fixedDelayString = "${monitor.detect-interval-ms:60000}", initialDelay = 5000)
    public void detect() {
        LocalDate today = monitorService.today();
        if (!calendar.isBizDay(today)) {
            return;
        }
        for (JobSnapshot s : monitorService.snapshot(today)) {
            Status status = s.getEval().getStatus();
            if (!status.isIssue()) {
                continue;
            }
            boolean created = repository.insertIssueIfAbsent(today, s.getJob().getJobId(), status,
                    s.getEval().getReason(), s.getEval().getSince());
            if (created) {
                // TODO 메신저/메일 알림은 여기서 보내면 된다
                log.warn("[배치 이슈] {} {} · {} — {}", s.getJob().getJobId(), s.getJob().getName(),
                        status.getLabel(), s.getEval().getReason());
            }
        }
    }
}
