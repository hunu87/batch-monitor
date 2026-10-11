package com.example.batchmonitor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class BatchMonitorApplication {

    public static void main(String[] args) {
        SpringApplication.run(BatchMonitorApplication.class, args);
    }
}
