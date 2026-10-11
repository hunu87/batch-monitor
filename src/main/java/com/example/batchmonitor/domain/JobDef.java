package com.example.batchmonitor.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalTime;

@Getter
@AllArgsConstructor
public class JobDef {
    private final String jobId;
    private final String name;
    private final Channel channel;
    private final ScheduleRule rule;
    private final LocalTime planTime;
    private final int toleranceMin;
    private final int expectedMin;
    private final String owner;
}
