package com.example.batchmonitor.service;

import com.example.batchmonitor.domain.JobDef;
import com.example.batchmonitor.domain.JobRun;
import com.example.batchmonitor.domain.RunStatus;
import com.example.batchmonitor.domain.ScheduleRule;
import com.example.batchmonitor.domain.Status;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

class StatusEvaluatorTest {

    private final StatusEvaluator evaluator = new StatusEvaluator(0.5);
    private final LocalDate day = LocalDate.of(2026, 10, 7);
    // 08:00 예정, 시작 허용 10분, 예상 소요 15분
    private final JobDef job = new JobDef("J1", "테스트", ScheduleRule.DAILY_BIZ, "매영업일",
            LocalTime.of(8, 0), 10, 15, "담당");
    private final LocalDateTime plan = day.atTime(8, 0);

    private Status eval(JobRun run, boolean scheduled, String now, Double avg) {
        return evaluator.evaluate(job, run, scheduled, plan, day.atTime(LocalTime.parse(now)), avg).getStatus();
    }

    private JobRun run(RunStatus status, int success, int fail) {
        return new JobRun(1, "J1", day, status, plan, status == RunStatus.RUNNING ? null : day.atTime(8, 5),
                null, success, fail, null);
    }

    @Test
    void 실행기록이_없으면_허용시간_전엔_대기_후엔_미실행() {
        assertThat(eval(null, true, "08:10", 100.0)).isEqualTo(Status.WAIT);
        assertThat(eval(null, true, "08:11", 100.0)).isEqualTo(Status.MISS);
    }

    @Test
    void 스케줄이_아닌_날은_해당없음() {
        assertThat(eval(null, false, "12:00", null)).isEqualTo(Status.NA);
    }

    @Test
    void 진행중은_예상소요를_넘으면_지연() {
        assertThat(eval(run(RunStatus.RUNNING, 10, 0), true, "08:15", 100.0)).isEqualTo(Status.RUN);
        assertThat(eval(run(RunStatus.RUNNING, 10, 0), true, "08:16", 100.0)).isEqualTo(Status.DELAY);
    }

    @Test
    void 실패보고는_실패() {
        assertThat(eval(run(RunStatus.FAILED, 10, 1), true, "08:06", 100.0)).isEqualTo(Status.FAIL);
    }

    @Test
    void 성공이어도_0건이거나_평소와_크게_다르면_확인필요() {
        assertThat(eval(run(RunStatus.SUCCESS, 0, 0), true, "09:00", 2300.0)).isEqualTo(Status.CHECK);
        assertThat(eval(run(RunStatus.SUCCESS, 40, 0), true, "09:00", 100.0)).isEqualTo(Status.CHECK);
        assertThat(eval(run(RunStatus.SUCCESS, 60, 0), true, "09:00", 100.0)).isEqualTo(Status.DONE);
        assertThat(eval(run(RunStatus.SUCCESS, 0, 0), true, "09:00", null)).isEqualTo(Status.DONE);
    }
}
