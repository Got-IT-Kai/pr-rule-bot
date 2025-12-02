plugins {
    java
    id("org.springframework.boot") version "3.5.3" apply false
    id("io.spring.dependency-management") version "1.1.7" apply false
    jacoco
    id("org.sonarqube") version "6.3.1.5724"
}

group = "com.code"
version = "0.0.1-SNAPSHOT"

// Root project configuration
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

// Version catalog - to be replaced with libs.versions.toml in Phase 2
val springAiVersion by extra("1.0.0")
val grpcVersion by extra("1.75.0")
val jtokkitVersion by extra("1.1.0")
val dotenvVersion by extra("2.3.2")
val mockitoInlineVersion by extra("5.2.0")
val blockhoundVersion by extra("1.0.8.RELEASE")
val mockwebserverVersion by extra("4.12.0")
val openTelemetryVersion by extra("1.43.0")
val micrometerTracingVersion by extra("1.4.0")
val testcontainersVersion by extra("1.20.4")
val restAssuredVersion by extra("5.5.0")
val awaitilityVersion by extra("4.2.2")

// Apply to all subprojects
subprojects {
    apply(plugin = "java")
    apply(plugin = "jacoco")

    repositories {
        mavenCentral()
    }

    java {
        toolchain {
            languageVersion = JavaLanguageVersion.of(21)
        }
    }

    // Common dependencies for all Spring Boot services
    if (name.endsWith("-service")) {
        apply(plugin = "org.springframework.boot")
        apply(plugin = "io.spring.dependency-management")

        dependencies {
            // Observability stack - OpenTelemetry + Micrometer
            implementation("io.micrometer:micrometer-tracing-bridge-otel")
            implementation("io.opentelemetry:opentelemetry-exporter-otlp")
            implementation("io.opentelemetry.instrumentation:opentelemetry-spring-boot-starter")
            implementation("io.micrometer:micrometer-registry-prometheus")
            // Note: micrometer-registry-otlp removed - Jaeger doesn't support OTLP metrics endpoint.
            //       We use Prometheus for metrics (pull model) and OTLP only for traces (push model).
            //       See docs/architecture/observability-architecture.md for details.

            // Spring Boot Actuator for health checks and metrics
            implementation("org.springframework.boot:spring-boot-starter-actuator")

            // Common test dependencies
            testImplementation("org.springframework.boot:spring-boot-starter-test")
            testImplementation("io.projectreactor:reactor-test")
            testRuntimeOnly("org.junit.platform:junit-platform-launcher")

            // Testcontainers for integration testing
            testImplementation(platform("org.testcontainers:testcontainers-bom:${rootProject.extra["testcontainersVersion"]}"))
            testImplementation("org.testcontainers:testcontainers")
            testImplementation("org.testcontainers:junit-jupiter")
            testImplementation("org.testcontainers:kafka")

            // REST Assured for API testing
            testImplementation("io.rest-assured:rest-assured:${rootProject.extra["restAssuredVersion"]}")
            testImplementation("io.rest-assured:json-path:${rootProject.extra["restAssuredVersion"]}")

            // Awaitility for async assertions
            testImplementation("org.awaitility:awaitility:${rootProject.extra["awaitilityVersion"]}")
        }
    }
}

// SonarQube configuration for multi-module project
sonar {
    properties {
        property("sonar.projectKey", "Got-IT-Kai_pr-rule-bot")
        property("sonar.organization", "got-it-kai")
        property("sonar.host.url", "https://sonarcloud.io")
        // Aggregate coverage from all submodules
        property("sonar.coverage.jacoco.xmlReportPaths",
            subprojects.map { "${it.layout.buildDirectory.get()}/reports/jacoco/test/jacocoTestReport.xml" }.joinToString(","))

        // Exclusions
        property("sonar.exclusions", ".github/workflows/**")

        // Coverage exclusions - infrastructure and boilerplate code
        property("sonar.coverage.exclusions",
            "**/config/**," +
            "**/dto/**," +
            "**/domain/model/**," +
            "**/*EventListener.java," +
            "**/*KafkaEventPublisher.java," +
            "**/*Application.java," +
            "**/OllamaAiClient.java," +
            "**/GeminiAiClient.java," +
            "**/AiClientHelper.java," +
            "**/platform-commons/src/main/java/com/code/platform/config/**," +
            "**/platform-commons/src/main/java/com/code/platform/github/**," +
            "**/platform-commons/src/main/java/com/code/platform/metrics/**")
    }
}
