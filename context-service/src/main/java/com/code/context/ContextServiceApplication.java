package com.code.context;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class ContextServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ContextServiceApplication.class, args);
    }
}
