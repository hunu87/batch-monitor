# 발송 배치 모니터링

알림톡·문자·이메일 발송 배치가 **오늘 제대로 돌았는지** 한 화면에서 보는 가벼운 모니터링 서버.
Spring Boot 2.5 + JdbcTemplate + H2(in-memory) + 정적 HTML/JS.

```bash
./mvnw spring-boot:run
# http://localhost:8080          대시보드
# http://localhost:8080/h2-console  (JDBC URL: jdbc:h2:mem:monitor)
```

기본 설정은 데모 모드다. 샘플 배치 10개를 넣고, 앱 시계를 `2026-10-07 09:20:30`부터 흐르게 해서
프로토타입과 같은 장면(실패·미실행·지연·확인필요)을 보여준다. 끄려면 `application.yml`에서
`monitor.demo.enabled: false`, `monitor.demo.start-at:` (빈 값).

## 상태 판정

| 상태 | 조건 |
|---|---|
| 해당없음 | 오늘 스케줄이 아님 (주말·`holiday` 테이블·`PROC_SCHD_PROD`가 매월 첫영업일 등) |
| 대기 | 실행 기록 없음, 아직 `예정(SCHD_TM) + 시작 허용(default-tolerance-min)` 전 |
| **미실행** | 실행 기록 없음, `예정 + 시작 허용` 경과 |
| 진행중 | RUNNING, `시작 + 예상 소요(default-expected-min)` 전 |
| **지연** | RUNNING, `시작 + 예상 소요` 경과 |
| **실패** | FAILED 보고 |
| **확인필요** | SUCCESS인데 0건, 또는 최근 10영업일 평균 대비 ±50% 이상 |
| 완료 | 그 외 SUCCESS |

굵은 상태는 `IssueDetector`가 1분마다 감지해 이슈 피드에 한 번씩 쌓는다.
알림(메신저·메일)은 `IssueDetector`의 TODO 자리에 붙이면 된다.
같은 날 재실행하면 마지막 실행 기준으로 판정한다.

## 배치 정의: `BAT_PROC_LST`

배치 정의는 실제 배치 관리 테이블 `BAT_PROC_LST`에서 읽는다. `USE_YN = 'Y'`이고 `SCHD_TM`이 있는 행만 판정한다.

| 컬럼 | 모니터에서의 쓰임 |
|---|---|
| `PRGM_KND` | 배치 ID. 보고 API의 `jobId`, `batch_run`·`batch_issue`의 `job_id` |
| `PRGM_NM` | 배치명 |
| `PROC_SCHD_PROD` | 스케줄 규칙. `매영업일`·`영업일`·`매일`·`평일` → 매영업일, `매월 첫영업일`·`월초` → 매월 첫영업일. 그 외 문구는 경고 로그를 남기고 매영업일로 판정 |
| `SCHD_TM` | 예정 시각 (`HH:MM` 또는 `HHMM`) |
| `MNGR` | 담당자 |

시작 허용 지연과 예상 소요는 이 테이블에 없어서 `application.yml`의 `monitor.default-tolerance-min`(기본 10분), `monitor.default-expected-min`(기본 30분)을 모든 배치에 적용한다.

## 배치에서 보고하는 API

```bash
# 시작 → runId 반환. bizDate 생략 시 오늘
curl -XPOST localhost:8080/api/runs -H 'Content-Type: application/json' \
  -d '{"jobId":"KT007","targetCount":2918}'

# (선택) 진행 건수
curl -XPUT localhost:8080/api/runs/1/progress -H 'Content-Type: application/json' \
  -d '{"successCount":1500,"failCount":3}'

# 종료: SUCCESS | FAILED
curl -XPUT localhost:8080/api/runs/1/finish -H 'Content-Type: application/json' \
  -d '{"status":"FAILED","successCount":300,"failCount":12,"errorMsg":"대행사 응답 타임아웃"}'
```

배치가 Spring Batch라면 `JobExecutionListener` 하나로 붙일 수 있다.

```java
public class MonitorReportListener implements JobExecutionListener {
    private final RestTemplate rest = new RestTemplate();
    private final String base = "http://monitor:8080/api/runs";
    private final String jobId; // BAT_PROC_LST.PRGM_KND

    public MonitorReportListener(String jobId) { this.jobId = jobId; }

    @Override
    public void beforeJob(JobExecution je) {
        Map<?, ?> res = rest.postForObject(base, Map.of("jobId", jobId), Map.class);
        je.getExecutionContext().putLong("monitorRunId", ((Number) res.get("runId")).longValue());
    }

    @Override
    public void afterJob(JobExecution je) {
        long runId = je.getExecutionContext().getLong("monitorRunId");
        int written = je.getStepExecutions().stream().mapToInt(s -> s.getWriteCount()).sum();
        boolean ok = je.getStatus() == BatchStatus.COMPLETED;
        Map<String, Object> body = new HashMap<>();
        body.put("status", ok ? "SUCCESS" : "FAILED");
        body.put("successCount", written);
        body.put("failCount", je.getStepExecutions().stream().mapToInt(s -> s.getWriteSkipCount()).sum());
        body.put("errorMsg", ok ? null : je.getAllFailureExceptions().toString());
        rest.put(base + "/" + runId + "/finish", body);
    }
}
```

> 모니터 서버가 죽어 있어도 발송 배치가 실패하지 않도록, 실제로는 보고 호출을 try/catch로 감싸 두는 게 좋다.

## 조회 API

- `GET /api/dashboard?date=2026-10-07` — 카드 건수, 배치 목록(문제 우선 정렬), 이슈. date 생략 시 오늘(휴일이면 직전 영업일)
- `GET /api/jobs/{jobId}/history?date=` — 최근 10영업일 처리 건수
- `POST /api/issues/{id}/ack` `{"acked":true}` — 이슈 확인 처리

## 운영으로 옮길 때

- `spring.datasource`를 실제 DB로 바꾸고 `schema.sql`을 해당 DB에 맞게 실행 (`spring.sql.init.mode`)
- `BAT_PROC_LST`는 운영 DB에 이미 있으므로 `schema.sql`에서 해당 `create` 문은 빼고 모니터 테이블(`batch_run`, `batch_issue`, `holiday`)만 만든다. `holiday`에 휴일을 넣는다 (휴일 변경 후 `BusinessCalendar.reload()` 또는 재기동)
- 인증이 필요하면 `spring-boot-starter-security` 추가
