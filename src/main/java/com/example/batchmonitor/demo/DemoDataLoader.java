package com.example.batchmonitor.demo;

import com.example.batchmonitor.domain.JobDef;
import com.example.batchmonitor.domain.RunStatus;
import com.example.batchmonitor.repository.MonitorRepository;
import com.example.batchmonitor.service.BusinessCalendar;
import com.example.batchmonitor.service.IssueDetector;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** 프로토타입과 같은 장면(실패·미실행·지연·확인필요 섞인 아침)을 만드는 샘플 데이터 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "monitor.demo.enabled", havingValue = "true")
public class DemoDataLoader implements ApplicationRunner {

    private final MonitorRepository repository;
    private final BusinessCalendar calendar;
    private final IssueDetector issueDetector;
    private final Clock clock;

    private static final String[][] HOLIDAYS_2026 = {
            {"2026-01-01", "신정"}, {"2026-02-16", "설날"}, {"2026-02-17", "설날"}, {"2026-02-18", "설날"},
            {"2026-03-02", "삼일절 대체"}, {"2026-05-05", "어린이날"}, {"2026-05-25", "부처님오신날 대체"},
            {"2026-06-03", "지방선거"}, {"2026-08-17", "광복절 대체"}, {"2026-09-24", "추석"},
            {"2026-09-25", "추석"}, {"2026-10-05", "개천절 대체"}, {"2026-10-09", "한글날"}, {"2026-12-25", "성탄절"},
    };

    /** 평소 처리 건수 */
    private static final Map<String, Integer> BASE = Map.of(
            "KT001", 17900, "SM004", 1270, "KT007", 2950, "SM009", 2300,
            "SM012", 6800, "EM003", 5400, "KT015", 12550, "SM020", 7200,
            "EM008", 3100, "EM001", 41200);

    @Override
    public void run(ApplicationArguments args) {
        for (String[] h : HOLIDAYS_2026) {
            repository.insertHoliday(LocalDate.parse(h[0]), h[1]);
        }
        calendar.reload();

        // BAT_PROC_LST 샘플. 허용·소요 시간은 테이블에 없어서 monitor.default-* 값이 적용된다
        repository.insertJob("KT001", "매매체결 안내 알림톡", "매영업일", "07:30", "김OO");
        repository.insertJob("SM004", "반대매매 예고 문자", "매영업일", "06:50", "이OO");
        repository.insertJob("KT007", "담보부족 안내 알림톡", "매영업일", "08:00", "김OO");
        repository.insertJob("SM009", "신용 만기 안내 문자", "매영업일", "08:10", "박OO");
        repository.insertJob("SM012", "배당금 입금 안내 문자", "매영업일", "08:30", "이OO");
        repository.insertJob("EM003", "공모주 청약 안내 이메일", "매영업일", "09:00", "박OO");
        repository.insertJob("KT015", "권리 발생 안내 알림톡", "매영업일", "09:00", "김OO");
        repository.insertJob("SM020", "주문 미체결 안내 문자", "매영업일", "07:00", "이OO");
        repository.insertJob("EM008", "만기 도래 안내 이메일", "매영업일", "10:00", "박OO");
        repository.insertJob("EM001", "월간 거래내역 이메일", "매월 첫영업일", "06:00", "박OO");
        List<JobDef> jobs = repository.findJobs();

        LocalDateTime now = LocalDateTime.now(clock);
        LocalDate today = calendar.latestBizDay(now.toLocalDate());

        // 지난 영업일: 전부 정상 종료
        Random rnd = new Random(7);
        for (LocalDate d : calendar.bizDaysBefore(today, 12)) {
            for (JobDef j : jobs) {
                if (!j.getRule().isScheduled(d, calendar)) {
                    continue;
                }
                int total = (int) Math.round(BASE.get(j.getJobId()) * (0.95 + rnd.nextDouble() * 0.1));
                int fail = (int) Math.round(total * 0.015);
                LocalDateTime start = d.atTime(j.getPlanTime());
                finished(j.getJobId(), d, start, start.plusMinutes(Math.max(1, j.getExpectedMin() * 3 / 4)),
                        RunStatus.SUCCESS, total, total - fail, fail, null);
            }
        }

        // 오늘: 프로토타입 장면. 예정 시각이 지난 것만 넣는다
        LocalDate t = today;
        if (!now.isBefore(t.atTime(6, 50))) finished("SM004", t, t.atTime(6, 50), t.atTime(6, 53), RunStatus.SUCCESS, 1212, 1200, 12, null);
        if (!now.isBefore(t.atTime(7, 0)))  finished("SM020", t, t.atTime(7, 0), t.atTime(7, 6), RunStatus.SUCCESS, 7469, 7400, 69, null);
        if (!now.isBefore(t.atTime(7, 30))) finished("KT001", t, t.atTime(7, 30), t.atTime(7, 42), RunStatus.SUCCESS, 18420, 17900, 520, null);
        if (!now.isBefore(t.atTime(8, 0)))  finished("KT007", t, t.atTime(8, 0), t.atTime(8, 4), RunStatus.FAILED, 2918, 300, 12,
                "SendAgentException: 대행사 응답 타임아웃 (30s) — 312/2,918건 처리 후 중단");
        if (!now.isBefore(t.atTime(8, 10))) finished("SM009", t, t.atTime(8, 10), t.atTime(8, 11), RunStatus.SUCCESS, 0, 0, 0, null);
        if (!now.isBefore(t.atTime(8, 30))) running("SM012", t, t.atTime(8, 30), 6800, 4100, 20);
        // EM003 은 실행 기록 없음 → 09:10 이후 미실행
        if (!now.isBefore(t.atTime(9, 0)))  running("KT015", t, t.atTime(9, 0), 12550, 9700, 180);

        issueDetector.detect();
    }

    private void finished(String jobId, LocalDate d, LocalDateTime start, LocalDateTime end, RunStatus status,
                          int target, int success, int fail, String error) {
        long id = repository.insertRun(jobId, d, start, target);
        repository.finishRun(id, status, end, success, fail, error);
    }

    private void running(String jobId, LocalDate d, LocalDateTime start, int target, int success, int fail) {
        long id = repository.insertRun(jobId, d, start, target);
        repository.updateProgress(id, success, fail);
    }
}
