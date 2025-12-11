package com.code.platform.config;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ComponentScan(basePackages = {
        "com.code.platform.idempotency",
        "com.code.platform.dlt"
})
public class IdempotencyAutoConfiguration {
}