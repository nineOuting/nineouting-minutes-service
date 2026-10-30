package com.nineouting.backend.minutes;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class MinutesServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(MinutesServiceApplication.class, args);
    }
}
