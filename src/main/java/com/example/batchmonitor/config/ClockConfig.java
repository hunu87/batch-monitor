package com.example.batchmonitor.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Configuration
public class ClockConfig {

    /**
     * 모든 "현재 시각"은 이 Clock에서 가져온다.
     * monitor.demo.start-at 이 있으면 그 시각부터 실제 속도로 흐르는 시계를 쓴다.
     */
    @Bean
    public Clock clock(@Value("${monitor.zone:Asia/Seoul}") String zone,
                       @Value("${monitor.demo.start-at:}") String startAt) {
        ZoneId zoneId = ZoneId.of(zone);
        Clock base = Clock.system(zoneId);
        if (startAt.isBlank()) {
            return base;
        }
        Instant target = LocalDateTime.parse(startAt).atZone(zoneId).toInstant();
        return Clock.offset(base, Duration.between(Instant.now(), target));
    }
}
