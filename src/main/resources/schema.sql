-- 배치 정의: 실제 배치 관리 테이블 BAT_PROC_LST (운영 DB에는 이미 있으므로 이 문장은 로컬 H2용)
-- 모니터는 USE_YN = 'Y' 이고 SCHD_TM 이 있는 행만 판정한다. PRGM_KND 가 batch_run/batch_issue 의 job_id 다.
create table if not exists BAT_PROC_LST (
    PRGM_KND       varchar2(5)    not null primary key,  -- 프로그램종류
    PRGM_NM        varchar2(100),                        -- 프로그램명
    PROC_SCHD_PROD varchar2(40),                         -- 처리예정주기 (예: 매영업일, 매월 첫영업일)
    PRGM_OPRT_FILE varchar2(200),                        -- 프로그램실행파일
    MNGR           varchar2(8),                          -- 담당자
    NOTE           varchar2(200),                        -- 비고
    SCHD_TM        varchar2(5),                          -- 스케줄시간 (HH:MM)
    SEND_COND      varchar2(2000),                       -- 발송조건
    ISSU_NO        varchar2(10),                         -- 이슈번호
    USE_YN         varchar2(1),                          -- 사용여부
    CUST_RPST      varchar2(12),                         -- 고객명단저장소
    TEL_RPST       varchar2(20),                         -- 전화번호저장소
    LOOP_CNT       number,                               -- 반복횟수
    RGST_DT        date,                                 -- 등록일자
    MDFY_DT        date                                  -- 수정일자
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
