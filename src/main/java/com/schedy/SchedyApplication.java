package com.schedy;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SchedyApplication {

    public static void main(String[] args) {
        SpringApplication.run(SchedyApplication.class, args);
    }
}
