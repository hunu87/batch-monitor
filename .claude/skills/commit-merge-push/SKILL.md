---
name: commit-merge-push
description: 현재 변경 사항을 작업 브랜치에 커밋하고, main에 fast-forward로 머지한 뒤 origin에 푸시한다.
disable-model-invocation: true
argument-hint: "[커밋 메시지 또는 변경 요약]"
---

# 커밋 → main 머지 → 원격 푸시

사용자가 `/commit-merge-push`를 호출했을 때만 실행한다. 인자(`$ARGUMENTS`)가 있으면 커밋 메시지나 변경 요약으로 쓴다.

## 1. 변경 확인

```bash
git status --short
git diff
git diff --staged
```

- 변경이 없으면 "커밋할 변경이 없다"고 알리고 멈춘다.
- `.env`, 키·비밀번호가 든 파일, 빌드 산출물(`target/` 등)은 커밋하지 않는다. 그런 파일이 있으면 사용자에게 먼저 알린다.

## 2. 작업 브랜치

- 지금 `main`이면 변경 내용에 맞는 브랜치를 만든다: `git checkout -b <유형>/<짧은-설명>`
  (유형 예: `feat`, `fix`, `docs`, `refactor`, `test`, `chore`)
- 이미 다른 브랜치에 있으면 그 브랜치를 그대로 쓴다.

## 3. 테스트

Java 소스(`src/`)나 `pom.xml`이 바뀌었으면 `./mvnw test`를 돌린다. 실패하면 출력을 보여주고 멈춘다.
문서만 바뀐 경우에는 건너뛰고, 건너뛰었다고 보고한다.

## 4. 커밋

- 관련 파일만 이름으로 지정해서 `git add`한다(`git add -A` 대신).
- 커밋 메시지는 이 저장소 관례대로 **한국어**로 쓴다. 첫 줄은 무엇을 왜 바꿨는지 짧게 적고, 필요하면 빈 줄 다음에 본문을 쓴다.
- 세션에서 안내한 attribution 줄(예: `Co-Authored-By: ...`)이 있으면 메시지 끝에 붙인다.

## 5. main에 머지

```bash
git checkout main
git pull --ff-only origin main
git merge --ff-only <작업 브랜치>
```

- `--ff-only` 머지가 실패하면(main이 앞서 나간 경우) 작업 브랜치를 `git rebase main`으로 갱신한 뒤 다시 머지한다.
- rebase 중에 충돌이 나면 직접 해결하지 말고 `git rebase --abort`를 실행한 다음, 충돌 파일을 사용자에게 보고하고 멈춘다.

## 6. 푸시

```bash
git push origin main
```

- 강제 푸시(`--force`, `--force-with-lease`)는 하지 않는다. 푸시가 거부되면 원인을 보고하고 멈춘다.

## 7. 정리와 보고

- 머지가 끝난 로컬 작업 브랜치는 `git branch -d <작업 브랜치>`로 지운다. 원격에 같은 브랜치가 있으면 지우지 말고 남아 있다고만 알린다.
- 마지막으로 다음을 짧게 보고한다: 커밋 해시와 메시지, 테스트를 돌렸는지와 그 결과, 푸시한 범위(`git log --oneline origin/main -3`), 지운 브랜치.
