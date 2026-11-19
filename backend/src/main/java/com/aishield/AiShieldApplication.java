package com.aishield;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class AiShieldApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiShieldApplication.class, args);
    }
}
