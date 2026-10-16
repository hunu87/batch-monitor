# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 개요

알림톡·문자·이메일 발송 배치가 오늘 제대로 돌았는지 한 화면에서 보는 가벼운 모니터링 서버.
Spring Boot 2.5 (Java 11) + JdbcTemplate + H2(in-memory) + 정적 HTML/JS 대시보드(`src/main/resources/static/`, 빌드 단계 없음).
README·주석·로그·UI 문구·테스트 메서드명이 모두 한국어이므로 새 코드도 이에 맞춘다.

## 명령어

```bash
./mvnw spring-boot:run                     # http://localhost:8080 대시보드, /h2-console (JDBC URL: jdbc:h2:mem:monitor)
./mvnw test                                # 전체 테스트
./mvnw test -Dtest=StatusEvaluatorTest     # 클래스 하나
./mvnw test -Dtest='StatusEvaluatorTest#실패보고는_실패'   # 메서드 하나
./mvnw package                             # jar 빌드
```

## 구조

**데이터 흐름.** 각 배치가 `RunReportController`로 직접 보고한다(`POST /api/runs` → `PUT /api/runs/{id}/progress` → `PUT /api/runs/{id}/finish`). 이 보고가 `batch_run`에 쌓인다. 계산된 상태는 어디에도 저장하지 않는다. 대시보드 요청과 `IssueDetector` 주기 실행 때마다 배치 정의 + 마지막 실행 기록 + 현재 시각으로 다시 계산한다.

- `MonitorService.snapshot(bizDate)`가 핵심이다. `batch_job` 전체와 최근 `history-days` 영업일의 (배치, 영업일)별 마지막 실행을 읽고, 평균 처리 건수(스케줄 대상이었고 성공한 날만)를 구한 뒤 배치마다 `StatusEvaluator`를 호출한다. `dashboard()`와 `IssueDetector` 모두 이 위에서 동작한다.
- `StatusEvaluator`는 순수 판정 로직(대기/미실행/진행중/지연/실패/확인필요/완료/해당없음)이며 단위 테스트가 있다. 판정 규칙을 바꾸면 `StatusEvaluatorTest`와 README의 상태 판정 표를 함께 고친다.
- `Status` enum은 한국어 라벨, 정렬용 `rank`(문제 상태가 위로), `issue` 여부를 가진다. 이슈 상태(FAIL, MISS, DELAY, CHECK)만 `IssueDetector`가 기록한다.
- `IssueDetector`(`@Scheduled`, `monitor.detect-interval-ms`)가 필요한 이유: "미실행"은 배치가 아무것도 보고하지 않아야 생기는 상태라 서버가 시간을 보고 판정해야만 잡힌다. `batch_issue`의 unique 제약으로 (영업일, 배치, 상태)당 한 번만 쌓인다. 메신저/메일 알림은 여기 `TODO` 자리에 붙인다.
- `BusinessCalendar`는 주말 + `holiday` 테이블 기준이며 휴일을 메모리에 캐시한다. 휴일을 바꾸면 `reload()`를 호출해야 한다. `ScheduleRule` enum(`DAILY_BIZ`, `MONTHLY_FIRST_BIZ`)이 이를 사용하므로 새 스케줄 유형은 여기에 추가한다.
- 같은 날 재실행하면 (배치, 영업일)별로 `run_id`가 가장 큰 실행만 판정에 쓴다(`MonitorRepository.findLatestRuns`).

**시각.** 모든 "현재 시각"은 주입받은 `java.time.Clock` 빈(`ClockConfig`)에서 가져온다. Clock 없이 `LocalDateTime.now()`를 쓰지 않는다. `monitor.demo.start-at`이 있으면 앱이 그 시각부터 실제 속도로 흐르는 시계를 쓴다.

**데모 모드.** `monitor.demo.enabled: true`(`application.yml` 기본값)이면 `DemoDataLoader`가 2026년 공휴일, 샘플 배치 10개, 약 12영업일치 이력, 실패·미실행·지연·확인필요가 섞인 "오늘" 장면을 넣고 감지를 한 번 돌린다. `start-at: 2026-10-07T09:20:30`과 함께 항상 같은 장면이 재현된다. 실제 시각으로 돌리려면 `monitor.demo.enabled: false`, `start-at`은 빈 값으로 둔다.

**스키마.** `src/main/resources/schema.sql`은 기동할 때마다 실행되며(`spring.sql.init.mode: always`) `create ... if not exists`를 쓴다. 영속 계층은 `MonitorRepository`의 직접 작성한 SQL과 `RowMapper`뿐이고 JPA는 쓰지 않는다. enum은 `name()`으로 저장한다.

**프론트엔드.** `static/app.js`가 주기적으로 `GET /api/dashboard`를 호출하고, `/api/jobs/{jobId}/history`, `/api/issues/{id}/ack`도 사용한다. 응답 형태는 `web/DashboardResponse`에 정의되어 있고, `diffText`·`runText` 같은 표시 문자열은 서버(`MonitorService.toRow`)에서 미리 만들어 내려준다.

**설정값** (`application.yml`의 `monitor.*`): `zone`, `detect-interval-ms`, `count-deviation-threshold`(처리 건수가 평균 대비 이 비율 이상 차이 나거나 0건이면 확인필요), `history-days`.
