package com.code.agent.config;

import io.github.cdimascio.dotenv.Dotenv;
import io.github.cdimascio.dotenv.DotenvEntry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
public class LocalApplicationInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        ConfigurableEnvironment environment = applicationContext.getEnvironment();

        if (Stream.of(environment.getActiveProfiles())
                .anyMatch(profile -> profile.equalsIgnoreCase("local"))) {
            Dotenv env = Dotenv.configure().ignoreIfMissing().load();
            Map<String, Object> collect = env.entries().stream()
                    .collect(Collectors.toMap(DotenvEntry::getKey, DotenvEntry::getValue));

            environment.getPropertySources().addFirst(new MapPropertySource("dotenvProperties", collect));
            log.info("Loaded environment variables from .env file for local profile");
        }
    }
}
