package com.example.batchmonitor.demo;

import com.example.batchmonitor.domain.Channel;
import com.example.batchmonitor.domain.JobDef;
import com.example.batchmonitor.domain.RunStatus;
import com.example.batchmonitor.domain.ScheduleRule;
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
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static com.example.batchmonitor.domain.Channel.EMAIL;
import static com.example.batchmonitor.domain.Channel.KAKAO;
import static com.example.batchmonitor.domain.Channel.SMS;
import static com.example.batchmonitor.domain.ScheduleRule.DAILY_BIZ;
import static com.example.batchmonitor.domain.ScheduleRule.MONTHLY_FIRST_BIZ;

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
            "MSG-KT-001", 17900, "MSG-SM-004", 1270, "MSG-KT-007", 2950, "MSG-SM-009", 2300,
            "MSG-SM-012", 6800, "MSG-EM-003", 5400, "MSG-KT-015", 12550, "MSG-SM-020", 7200,
            "MSG-EM-008", 3100, "MSG-EM-001", 41200);

    @Override
    public void run(ApplicationArguments args) {
        for (String[] h : HOLIDAYS_2026) {
            repository.insertHoliday(LocalDate.parse(h[0]), h[1]);
        }
        calendar.reload();

        List<JobDef> jobs = List.of(
                job("MSG-KT-001", "매매체결 안내 알림톡", KAKAO, DAILY_BIZ, "07:30", 10, 15, "김OO"),
                job("MSG-SM-004", "반대매매 예고 문자", SMS, DAILY_BIZ, "06:50", 5, 5, "이OO"),
                job("MSG-KT-007", "담보부족 안내 알림톡", KAKAO, DAILY_BIZ, "08:00", 10, 10, "김OO"),
                job("MSG-SM-009", "신용 만기 안내 문자", SMS, DAILY_BIZ, "08:10", 10, 5, "박OO"),
                job("MSG-SM-012", "배당금 입금 안내 문자", SMS, DAILY_BIZ, "08:30", 15, 60, "이OO"),
                job("MSG-EM-003", "공모주 청약 안내 이메일", EMAIL, DAILY_BIZ, "09:00", 10, 20, "박OO"),
                job("MSG-KT-015", "권리 발생 안내 알림톡", KAKAO, DAILY_BIZ, "09:00", 15, 15, "김OO"),
                job("MSG-SM-020", "주문 미체결 안내 문자", SMS, DAILY_BIZ, "07:00", 10, 10, "이OO"),
                job("MSG-EM-008", "만기 도래 안내 이메일", EMAIL, DAILY_BIZ, "10:00", 10, 20, "박OO"),
                job("MSG-EM-001", "월간 거래내역 이메일", EMAIL, MONTHLY_FIRST_BIZ, "06:00", 30, 60, "박OO"));
        jobs.forEach(repository::insertJob);

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
                int fail = (int) Math.round(total * (j.getChannel() == KAKAO ? 0.025 : 0.01));
                LocalDateTime start = d.atTime(j.getPlanTime());
                finished(j.getJobId(), d, start, start.plusMinutes(Math.max(1, j.getExpectedMin() * 3 / 4)),
                        RunStatus.SUCCESS, total, total - fail, fail, null);
            }
        }

        // 오늘: 프로토타입 장면. 예정 시각이 지난 것만 넣는다
        LocalDate t = today;
        if (!now.isBefore(t.atTime(6, 50))) finished("MSG-SM-004", t, t.atTime(6, 50), t.atTime(6, 53), RunStatus.SUCCESS, 1212, 1200, 12, null);
        if (!now.isBefore(t.atTime(7, 0)))  finished("MSG-SM-020", t, t.atTime(7, 0), t.atTime(7, 6), RunStatus.SUCCESS, 7469, 7400, 69, null);
        if (!now.isBefore(t.atTime(7, 30))) finished("MSG-KT-001", t, t.atTime(7, 30), t.atTime(7, 42), RunStatus.SUCCESS, 18420, 17900, 520, null);
        if (!now.isBefore(t.atTime(8, 0)))  finished("MSG-KT-007", t, t.atTime(8, 0), t.atTime(8, 4), RunStatus.FAILED, 2918, 300, 12,
                "SendAgentException: 대행사 응답 타임아웃 (30s) — 312/2,918건 처리 후 중단");
        if (!now.isBefore(t.atTime(8, 10))) finished("MSG-SM-009", t, t.atTime(8, 10), t.atTime(8, 11), RunStatus.SUCCESS, 0, 0, 0, null);
        if (!now.isBefore(t.atTime(8, 30))) running("MSG-SM-012", t, t.atTime(8, 30), 6800, 4100, 20);
        // MSG-EM-003 은 실행 기록 없음 → 09:10 이후 미실행
        if (!now.isBefore(t.atTime(9, 0)))  running("MSG-KT-015", t, t.atTime(9, 0), 12550, 9700, 180);

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

    private static JobDef job(String id, String name, Channel ch, ScheduleRule rule, String plan,
                              int tol, int expected, String owner) {
        return new JobDef(id, name, ch, rule, LocalTime.parse(plan), tol, expected, owner);
    }
}
