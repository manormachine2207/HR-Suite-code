package io.github.manormachine2207.hrsuite;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class HrSuiteApplication {

    public static void main(String[] args) {
        SpringApplication.run(HrSuiteApplication.class, args);
    }
}
