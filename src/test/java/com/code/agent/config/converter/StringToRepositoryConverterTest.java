package com.code.agent.config.converter;

import com.code.agent.config.CliProperties;
import com.code.agent.domain.model.Repository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.ConfigurationPropertiesBinding;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.BindException;
import org.springframework.boot.context.properties.bind.validation.BindValidationException;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;

import static org.assertj.core.api.Assertions.assertThat;

class StringToRepositoryConverterTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class)
            .withPropertyValues(
                    "cli.repository=o/r",
                    "cli.pr-number=1",
                    "cli.time-out-minutes=1"
            );

    @Configuration
    @EnableConfigurationProperties(CliProperties.class)
    static class TestConfig {
        @Bean
        @ConfigurationPropertiesBinding
        public Converter<String, Repository> stringToRepositoryConverter() {
            return new StringToRepositoryConverter();
        }
    }

    @Test
    void bindStringToRepository() {
        contextRunner.run(context -> {
            CliProperties properties = context.getBean(CliProperties.class);
            assertThat(properties.repository().owner()).isEqualTo("o");
            assertThat(properties.repository().name()).isEqualTo("r");
            assertThat(properties.prNumber()).isEqualTo(1);
            assertThat(properties.timeOutMinutes()).isEqualTo(1);
        });
    }

    @Test
    void invalidRepositoryFormat() {
        ApplicationContextRunner invalidContextRunner = new ApplicationContextRunner()
                .withUserConfiguration(TestConfig.class)
                .withPropertyValues(
                        "cli.repository=invalidFormat",
                        "cli.pr-number=1",
                        "cli.time-out-minutes=1"
                );

        invalidContextRunner.run(context ->
                assertThat(context.getStartupFailure()).isNotNull());
    }

    @Test
    void emptyRepository() {
        ApplicationContextRunner emptyContextRunner = new ApplicationContextRunner()
                .withUserConfiguration(TestConfig.class)
                .withPropertyValues(
                        "cli.repository=",
                        "cli.pr-number=1",
                        "cli.time-out-minutes=1"
                );

        emptyContextRunner.run(context ->
                assertThat(context.getStartupFailure()).isNotNull()
                .hasCauseInstanceOf(BindException.class)
                .hasRootCauseInstanceOf(BindValidationException.class));
    }

}