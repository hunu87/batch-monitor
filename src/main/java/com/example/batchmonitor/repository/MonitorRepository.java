package com.example.batchmonitor.repository;

import com.example.batchmonitor.domain.Channel;
import com.example.batchmonitor.domain.JobDef;
import com.example.batchmonitor.domain.JobRun;
import com.example.batchmonitor.domain.RunStatus;
import com.example.batchmonitor.domain.ScheduleRule;
import com.example.batchmonitor.domain.Status;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
@RequiredArgsConstructor
public class MonitorRepository {

    private final JdbcTemplate jdbc;

    private static final RowMapper<JobDef> JOB = (rs, i) -> new JobDef(
            rs.getString("job_id"),
            rs.getString("name"),
            Channel.valueOf(rs.getString("channel")),
            ScheduleRule.valueOf(rs.getString("schedule_rule")),
            rs.getTime("plan_time").toLocalTime(),
            rs.getInt("tolerance_min"),
            rs.getInt("expected_min"),
            rs.getString("owner"));

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
        return jdbc.query("select * from batch_job order by plan_time, job_id", JOB);
    }

    public Optional<JobDef> findJob(String jobId) {
        return jdbc.query("select * from batch_job where job_id = ?", JOB, jobId).stream().findFirst();
    }

    public void insertJob(JobDef j) {
        jdbc.update("insert into batch_job (job_id, name, channel, schedule_rule, plan_time, tolerance_min, expected_min, owner) "
                        + "values (?, ?, ?, ?, ?, ?, ?, ?)",
                j.getJobId(), j.getName(), j.getChannel().name(), j.getRule().name(),
                j.getPlanTime(), j.getToleranceMin(), j.getExpectedMin(), j.getOwner());
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

    private static LocalDateTime toLocalDateTime(Timestamp ts) {
        return ts == null ? null : ts.toLocalDateTime();
    }
}
