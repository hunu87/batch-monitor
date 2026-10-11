package com.example.batchmonitor.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum Channel {
    KAKAO("카톡", "카톡 (알림톡)"),
    SMS("문자", "문자 (SMS·LMS)"),
    EMAIL("이메일", "이메일");

    private final String label;
    private final String longLabel;
}
