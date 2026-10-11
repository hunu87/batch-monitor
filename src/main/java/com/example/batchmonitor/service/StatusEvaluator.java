package com.example.batchmonitor.service;

import com.example.batchmonitor.domain.JobDef;
import com.example.batchmonitor.domain.JobRun;
import com.example.batchmonitor.domain.Status;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 배치 정의 + 오늘 실행 기록 + 현재 시각 → 대시보드 상태.
 *
 * <pre>
 * 실행 기록 없음   : 스케줄 아님 → 해당없음 / 예정+허용 경과 → 미실행 / 그 전 → 대기
 * FAILED          : 실패
 * RUNNING         : 시작+예상소요 경과 → 지연 / 그 전 → 진행중
 * SUCCESS         : 0건이거나 평소 대비 ±threshold 이상 → 확인필요 / 그 외 → 완료
 * </pre>
 */
@Component
public class StatusEvaluator {

    private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("HH:mm");

    private final double threshold;

    public StatusEvaluator(@Value("${monitor.count-deviation-threshold:0.5}") double threshold) {
        this.threshold = threshold;
    }

    public Evaluation evaluate(JobDef job, JobRun run, boolean scheduled, LocalDateTime planAt,
                               LocalDateTime now, Double avg) {
        if (run == null) {
            if (!scheduled) {
                return new Evaluation(Status.NA, null, null);
            }
            LocalDateTime deadline = planAt.plusMinutes(job.getToleranceMin());
            if (now.isAfter(deadline)) {
                return new Evaluation(Status.MISS, deadline,
                        String.format("예정 %s + 허용 %d분 경과, 실행 기록 없음", planAt.format(HM), job.getToleranceMin()));
            }
            return new Evaluation(Status.WAIT, null, null);
        }

        switch (run.getStatus()) {
            case FAILED:
                return new Evaluation(Status.FAIL, orNow(run.getEndedAt(), now),
                        run.getErrorMsg() != null ? run.getErrorMsg() : "실패 종료");
            case RUNNING:
                LocalDateTime expectedEnd = run.getStartedAt().plusMinutes(job.getExpectedMin());
                if (now.isAfter(expectedEnd)) {
                    return new Evaluation(Status.DELAY, expectedEnd,
                            String.format("예상 종료 %s 초과 (예상 소요 %d분)", expectedEnd.format(HM), job.getExpectedMin()));
                }
                return new Evaluation(Status.RUN, null, null);
            case SUCCESS:
            default:
                String reason = countAnomaly(run.processed(), avg);
                if (reason != null) {
                    return new Evaluation(Status.CHECK, orNow(run.getEndedAt(), now), reason);
                }
                return new Evaluation(Status.DONE, null, null);
        }
    }

    private String countAnomaly(int processed, Double avg) {
        if (avg == null || avg <= 0) {
            return null;
        }
        if (processed == 0) {
            return String.format("정상 종료되었으나 발송 0건 (평소 약 %,d건) — 대상 추출 확인 필요", Math.round(avg));
        }
        double diff = (processed - avg) / avg;
        if (Math.abs(diff) >= threshold) {
            return String.format("처리 %,d건, 평소(약 %,d건) 대비 %+d%%", processed, Math.round(avg), Math.round(diff * 100));
        }
        return null;
    }

    private static LocalDateTime orNow(LocalDateTime t, LocalDateTime now) {
        return t != null ? t : now;
    }

    @Getter
    @AllArgsConstructor
    public static class Evaluation {
        private final Status status;
        /** 이슈가 발생한 시각 (이슈 상태일 때만) */
        private final LocalDateTime since;
        /** 이슈 사유 (이슈 상태일 때만) */
        private final String reason;
    }
}
