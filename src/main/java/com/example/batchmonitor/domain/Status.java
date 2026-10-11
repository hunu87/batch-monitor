package com.example.batchmonitor.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 대시보드에 보이는 배치 상태. rank 가 작을수록 위에 정렬된다. */
@Getter
@RequiredArgsConstructor
public enum Status {
    FAIL("실패", 0, true),
    MISS("미실행", 1, true),
    DELAY("지연", 2, true),
    CHECK("확인필요", 3, true),
    RUN("진행중", 4, false),
    WAIT("대기", 5, false),
    DONE("완료", 6, false),
    NA("해당없음", 7, false);

    private final String label;
    private final int rank;
    private final boolean issue;
}
