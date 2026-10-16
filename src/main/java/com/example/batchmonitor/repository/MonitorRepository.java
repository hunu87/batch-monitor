package com.example.batchmonitor.repository;

import com.example.batchmonitor.domain.JobDef;
import com.example.batchmonitor.domain.JobRun;
import com.example.batchmonitor.domain.RunStatus;
import com.example.batchmonitor.domain.ScheduleRule;
import com.example.batchmonitor.domain.Status;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Repository
public class MonitorRepository {

    /** 판정 대상 배치: 사용 중이고 스케줄 시각이 있는 것 */
    private static final String JOB_SQL = "select PRGM_KND, PRGM_NM, PROC_SCHD_PROD, SCHD_TM, MNGR from BAT_PROC_LST "
            + "where USE_YN = 'Y' and SCHD_TM is not null";

    private final JdbcTemplate jdbc;
    private final int defaultToleranceMin;
    private final int defaultExpectedMin;

    public MonitorRepository(JdbcTemplate jdbc,
                             @Value("${monitor.default-tolerance-min:10}") int defaultToleranceMin,
                             @Value("${monitor.default-expected-min:30}") int defaultExpectedMin) {
        this.jdbc = jdbc;
        this.defaultToleranceMin = defaultToleranceMin;
        this.defaultExpectedMin = defaultExpectedMin;
    }

    /** SCHD_TM 을 읽을 수 없는 행은 null → 호출 쪽에서 제외 */
    private JobDef mapJob(ResultSet rs, int rowNum) throws SQLException {
        String id = rs.getString("PRGM_KND");
        LocalTime plan = parseTime(id, rs.getString("SCHD_TM"));
        if (plan == null) {
            return null;
        }
        String ruleText = rs.getString("PROC_SCHD_PROD");
        ScheduleRule rule = ScheduleRule.fromText(ruleText);
        if (rule == null) {
            log.warn("[배치 정의] {} 처리예정주기 '{}' 를 해석하지 못해 매영업일로 판정", id, ruleText);
            rule = ScheduleRule.DAILY_BIZ;
        }
        return new JobDef(id, rs.getString("PRGM_NM"), rule, ruleText != null ? ruleText : rule.getLabel(),
                plan, defaultToleranceMin, defaultExpectedMin, rs.getString("MNGR"));
    }

    private static final RowMapper<JobRun> RUN = (rs, i) -> new JobRun(
            rs.getLong("run_id"),
            rs.getString("job_id"),
            rs.getDate("biz_date").toLocalDate(),
            RunStatus.valueOf(rs.getString("status")),
            rs.getTimestamp("started_at").toLocalDateTime(),
            toLocalDateTime(rs.getTimestamp("ended_at")),
            rs.getObject("target_count", Integer.class),
            rs.getInt("success_count"),
            rs.getInt("fail_count"),
            rs.getString("error_msg"));

    private static final RowMapper<IssueRow> ISSUE = (rs, i) -> new IssueRow(
            rs.getLong("issue_id"),
            rs.getString("job_id"),
            Status.valueOf(rs.getString("status")),
            rs.getString("message"),
            rs.getTimestamp("occurred_at").toLocalDateTime(),
            rs.getBoolean("acked"));

    // ---- 배치 정의 ----

    public List<JobDef> findJobs() {
        return jdbc.query(JOB_SQL + " order by SCHD_TM, PRGM_KND", this::mapJob).stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    public Optional<JobDef> findJob(String jobId) {
        return jdbc.query(JOB_SQL + " and PRGM_KND = ?", this::mapJob, jobId).stream()
                .filter(Objects::nonNull)
                .findFirst();
    }

    /** 데모용. 실제 BAT_PROC_LST 는 배치 관리 화면에서 관리한다 */
    public void insertJob(String prgmKnd, String prgmNm, String procSchdProd, String schdTm, String mngr) {
        jdbc.update("insert into BAT_PROC_LST (PRGM_KND, PRGM_NM, PROC_SCHD_PROD, SCHD_TM, MNGR, USE_YN, RGST_DT) "
                        + "values (?, ?, ?, ?, ?, 'Y', current_date)",
                prgmKnd, prgmNm, procSchdProd, schdTm, mngr);
    }

    // ---- 실행 기록 ----

    /** 기간 내 (배치, 영업일)별 마지막 실행만 */
    public List<JobRun> findLatestRuns(LocalDate from, LocalDate to) {
        return jdbc.query("select r.* from batch_run r "
                + "where r.biz_date between ? and ? "
                + "and r.run_id = (select max(x.run_id) from batch_run x where x.job_id = r.job_id and x.biz_date = r.biz_date)",
                RUN, from, to);
    }

    public Optional<JobRun> findRun(long runId) {
        return jdbc.query("select * from batch_run where run_id = ?", RUN, runId).stream().findFirst();
    }

    public long insertRun(String jobId, LocalDate bizDate, LocalDateTime startedAt, Integer targetCount) {
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "insert into batch_run (job_id, biz_date, status, started_at, target_count) values (?, ?, ?, ?, ?)",
                    new String[]{"run_id"});
            ps.setString(1, jobId);
            ps.setObject(2, bizDate);
            ps.setString(3, RunStatus.RUNNING.name());
            ps.setTimestamp(4, Timestamp.valueOf(startedAt));
            ps.setObject(5, targetCount);
            return ps;
        }, key);
        return key.getKey().longValue();
    }

    public void updateProgress(long runId, int successCount, int failCount) {
        jdbc.update("update batch_run set success_count = ?, fail_count = ? where run_id = ?",
                successCount, failCount, runId);
    }

    public void finishRun(long runId, RunStatus status, LocalDateTime endedAt, int successCount, int failCount, String errorMsg) {
        jdbc.update("update batch_run set status = ?, ended_at = ?, success_count = ?, fail_count = ?, error_msg = ? where run_id = ?",
                status.name(), endedAt, successCount, failCount, errorMsg, runId);
    }

    // ---- 휴일 ----

    public Set<LocalDate> findHolidays() {
        return new HashSet<>(jdbc.query("select holiday_date from holiday",
                (rs, i) -> rs.getDate(1).toLocalDate()));
    }

    public void insertHoliday(LocalDate date, String name) {
        jdbc.update("insert into holiday (holiday_date, name) values (?, ?)", date, name);
    }

    // ---- 이슈 ----

    /** 처음 감지된 이슈면 true */
    public boolean insertIssueIfAbsent(LocalDate bizDate, String jobId, Status status, String message, LocalDateTime occurredAt) {
        Integer exists = jdbc.queryForObject(
                "select count(*) from batch_issue where biz_date = ? and job_id = ? and status = ?",
                Integer.class, bizDate, jobId, status.name());
        if (exists != null && exists > 0) {
            return false;
        }
        try {
            jdbc.update("insert into batch_issue (biz_date, job_id, status, message, occurred_at) values (?, ?, ?, ?, ?)",
                    bizDate, jobId, status.name(), message, occurredAt);
            return true;
        } catch (DuplicateKeyException e) {
            return false;
        }
    }

    public List<IssueRow> findIssues(LocalDate bizDate) {
        return jdbc.query("select * from batch_issue where biz_date = ? order by occurred_at desc, issue_id desc",
                ISSUE, bizDate);
    }

    public int ackIssue(long issueId, boolean acked) {
        return jdbc.update("update batch_issue set acked = ? where issue_id = ?", acked, issueId);
    }

    /** SCHD_TM: "HH:MM" 또는 "HHMM" */
    private static LocalTime parseTime(String jobId, String text) {
        String t = text.trim();
        if (t.matches("\\d{4}")) {
            t = t.substring(0, 2) + ":" + t.substring(2);
        }
        try {
            return LocalTime.parse(t.length() == 4 ? "0" + t : t);
        } catch (DateTimeParseException e) {
            log.warn("[배치 정의] {} 스케줄시간 '{}' 형식 오류로 판정에서 제외", jobId, text);
            return null;
        }
    }

    private static LocalDateTime toLocalDateTime(Timestamp ts) {
        return ts == null ? null : ts.toLocalDateTime();
    }
}
