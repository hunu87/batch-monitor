-- 배치 정의: 언제 돌아야 하고, 얼마나 늦으면 문제인지
create table if not exists batch_job (
    job_id        varchar(40)  primary key,
    name          varchar(100) not null,
    channel       varchar(20)  not null,  -- KAKAO / SMS / EMAIL
    schedule_rule varchar(30)  not null,  -- DAILY_BIZ / MONTHLY_FIRST_BIZ
    plan_time     time         not null,  -- 예정 시작 시각
    tolerance_min int          not null,  -- 시작 허용 지연(분). 넘으면 '미실행'
    expected_min  int          not null,  -- 예상 소요(분). 넘으면 '지연'
    owner         varchar(50)
);

-- 실행 기록: 배치가 시작/진행/종료 시 API로 남김. 같은 날 재실행하면 마지막 건 기준
create table if not exists batch_run (
    run_id        bigint auto_increment primary key,
    job_id        varchar(40) not null,
    biz_date      date        not null,
    status        varchar(20) not null,  -- RUNNING / SUCCESS / FAILED
    started_at    timestamp   not null,
    ended_at      timestamp,
    target_count  int,
    success_count int default 0 not null,
    fail_count    int default 0 not null,
    error_msg     varchar(1000)
);
create index if not exists ix_batch_run_job_date on batch_run (job_id, biz_date);

create table if not exists holiday (
    holiday_date date primary key,
    name         varchar(50)
);

-- 감지된 이슈. 같은 날 같은 배치·상태는 한 번만 쌓임
create table if not exists batch_issue (
    issue_id    bigint auto_increment primary key,
    biz_date    date         not null,
    job_id      varchar(40)  not null,
    status      varchar(20)  not null,
    message     varchar(1000),
    occurred_at timestamp    not null,
    acked       boolean default false not null,
    constraint uq_batch_issue unique (biz_date, job_id, status)
);
