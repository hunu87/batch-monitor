package com.example.batchmonitor.service;

import com.example.batchmonitor.domain.JobDef;
import com.example.batchmonitor.domain.JobRun;
import com.example.batchmonitor.domain.RunStatus;
import com.example.batchmonitor.domain.Status;
import com.example.batchmonitor.repository.IssueRow;
import com.example.batchmonitor.repository.MonitorRepository;
import com.example.batchmonitor.service.StatusEvaluator.Evaluation;
import com.example.batchmonitor.web.DashboardResponse;
import com.example.batchmonitor.web.DashboardResponse.HistoryPoint;
import com.example.batchmonitor.web.DashboardResponse.IssueView;
import com.example.batchmonitor.web.DashboardResponse.JobRow;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class MonitorService {

    private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter HMS = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter MD = DateTimeFormatter.ofPattern("M/d");

    private final MonitorRepository repository;
    private final BusinessCalendar calendar;
    private final StatusEvaluator evaluator;
    private final Clock clock;
    private final int historyDays;

    public MonitorService(MonitorRepository repository, BusinessCalendar calendar, StatusEvaluator evaluator,
                          Clock clock, @Value("${monitor.history-days:10}") int historyDays) {
        this.repository = repository;
        this.calendar = calendar;
        this.evaluator = evaluator;
        this.clock = clock;
        this.historyDays = historyDays;
    }

    public LocalDate today() {
        return LocalDate.now(clock);
    }

    /** 영업일 하루치 전 배치의 상태 계산 */
    public List<JobSnapshot> snapshot(LocalDate bizDate) {
        LocalDateTime now = LocalDateTime.now(clock);
        List<LocalDate> prevDays = calendar.bizDaysBefore(bizDate, historyDays);
        Map<String, Map<LocalDate, JobRun>> runs = runsByJob(prevDays.get(0), bizDate);

        List<JobSnapshot> result = new ArrayList<>();
        for (JobDef job : repository.findJobs()) {
            Map<LocalDate, JobRun> byDate = runs.getOrDefault(job.getJobId(), Map.of());
            JobRun run = byDate.get(bizDate);
            Double avg = average(job, byDate, prevDays);
            boolean scheduled = job.getRule().isScheduled(bizDate, calendar);
            Evaluation eval = evaluator.evaluate(job, run, scheduled, bizDate.atTime(job.getPlanTime()), now, avg);
            result.add(new JobSnapshot(job, run, avg, eval));
        }
        return result;
    }

    public DashboardResponse dashboard(LocalDate bizDate) {
        List<JobSnapshot> snaps = snapshot(bizDate);
        snaps.sort(Comparator.<JobSnapshot>comparingInt(s -> s.getEval().getStatus().getRank())
                .thenComparing(s -> s.getJob().getPlanTime()));

        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put("ALL", 0);
        for (Status s : Status.values()) {
            counts.put(s.name(), 0);
        }
        for (JobSnapshot s : snaps) {
            counts.merge(s.getEval().getStatus().name(), 1, Integer::sum);
            if (s.getEval().getStatus() != Status.NA) {
                counts.merge("ALL", 1, Integer::sum);
            }
        }

        Map<String, String> names = snaps.stream()
                .collect(Collectors.toMap(s -> s.getJob().getJobId(), s -> s.getJob().getName()));
        List<IssueView> issues = new ArrayList<>();
        for (IssueRow i : repository.findIssues(bizDate)) {
            issues.add(IssueView.builder()
                    .id(i.getIssueId())
                    .jobId(i.getJobId())
                    .time(i.getOccurredAt().format(HM))
                    .title(names.getOrDefault(i.getJobId(), i.getJobId()) + " · " + i.getStatus().getLabel())
                    .desc(i.getMessage())
                    .acked(i.isAcked())
                    .build());
        }

        return DashboardResponse.builder()
                .bizDate(bizDate.toString())
                .bizDay(calendar.isBizDay(bizDate))
                .now(LocalDateTime.now(clock).format(HMS))
                .counts(counts)
                .rows(snaps.stream().map(this::toRow).collect(Collectors.toList()))
                .issues(issues)
                .build();
    }

    /** 최근 N영업일 처리 건수 (오늘 포함, 오래된 순) */
    public List<HistoryPoint> history(String jobId, LocalDate bizDate) {
        repository.findJob(jobId).orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "unknown job " + jobId));
        List<LocalDate> days = new ArrayList<>(calendar.bizDaysBefore(bizDate, historyDays - 1));
        days.add(bizDate);
        Map<LocalDate, JobRun> byDate = runsByJob(days.get(0), bizDate).getOrDefault(jobId, Map.of());
        return days.stream()
                .map(d -> new HistoryPoint(d.format(MD), byDate.containsKey(d) ? byDate.get(d).processed() : 0))
                .collect(Collectors.toList());
    }

    private JobRow toRow(JobSnapshot s) {
        JobDef job = s.getJob();
        JobRun run = s.getRun();
        Status status = s.getEval().getStatus();
        Integer processed = run == null ? null : run.processed();

        String diffText = "–";
        boolean diffAlert = false;
        if (run != null && (status == Status.RUN || status == Status.DELAY)) {
            Integer base = run.getTargetCount() != null && run.getTargetCount() > 0 ? run.getTargetCount()
                    : s.getAvg() != null ? (int) Math.round(s.getAvg()) : null;
            if (base != null && base > 0) {
                diffText = Math.round(processed * 100.0 / base) + "% 진행";
            }
        } else if (run != null && s.getAvg() != null && s.getAvg() > 0) {
            long p = Math.round((processed - s.getAvg()) / s.getAvg() * 100);
            diffText = (p > 0 ? "+" : "") + p + "%";
            diffAlert = Math.abs(p) >= 30;
        }

        String runText = "–";
        if (run != null) {
            runText = run.getStartedAt().format(HM) + " – "
                    + (run.getEndedAt() != null ? run.getEndedAt().format(HM) : "진행중");
        }

        return JobRow.builder()
                .jobId(job.getJobId())
                .name(job.getName())
                .rule(job.getRuleText())
                .plan(job.getPlanTime().format(HM))
                .toleranceMin(job.getToleranceMin())
                .expectedMin(job.getExpectedMin())
                .owner(job.getOwner())
                .runText(runText)
                .status(status.name())
                .statusLabel(status.getLabel())
                .processed(processed)
                .avg(s.getAvg() == null ? null : Math.round(s.getAvg()))
                .diffText(diffText)
                .diffAlert(diffAlert)
                .reason(s.getEval().getReason())
                .build();
    }

    /** 이전 영업일 중 스케줄 대상이고 성공한 날의 평균 처리 건수 */
    private Double average(JobDef job, Map<LocalDate, JobRun> byDate, List<LocalDate> prevDays) {
        double total = 0;
        int n = 0;
        for (LocalDate d : prevDays) {
            JobRun r = byDate.get(d);
            if (r != null && r.getStatus() == RunStatus.SUCCESS && job.getRule().isScheduled(d, calendar)) {
                total += r.processed();
                n++;
            }
        }
        return n == 0 ? null : total / n;
    }

    private Map<String, Map<LocalDate, JobRun>> runsByJob(LocalDate from, LocalDate to) {
        Map<String, Map<LocalDate, JobRun>> map = new HashMap<>();
        for (JobRun r : repository.findLatestRuns(from, to)) {
            map.computeIfAbsent(r.getJobId(), k -> new HashMap<>()).put(r.getBizDate(), r);
        }
        return map;
    }

    @Getter
    @AllArgsConstructor
    public static class JobSnapshot {
        private final JobDef job;
        private final JobRun run;
        private final Double avg;
        private final Evaluation eval;
    }
}
