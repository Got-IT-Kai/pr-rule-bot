package com.code.platform.config;

import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationPropertiesScan("com.code.platform")
public class PlatformPropertiesAutoConfiguration {
}
