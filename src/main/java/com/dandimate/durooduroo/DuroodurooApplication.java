/** 두루두루 Spring Boot 애플리케이션을 시작한다. */
package com.dandimate.durooduroo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class DuroodurooApplication {

    public static void main(String[] args) {
        SpringApplication.run(DuroodurooApplication.class, args);
    }
}
