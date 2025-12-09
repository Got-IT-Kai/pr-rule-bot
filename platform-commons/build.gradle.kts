plugins {
    id("java-library")
}

group = "com.code"
version = "0.1.0"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // Spring Boot BOM for version management (apply to all configurations)
    implementation(platform("org.springframework.boot:spring-boot-dependencies:3.5.3"))
    annotationProcessor(platform("org.springframework.boot:spring-boot-dependencies:3.5.3"))

    // OpenTelemetry BOM for version management
    api(platform("io.opentelemetry:opentelemetry-bom:1.43.0"))
    api(platform("io.opentelemetry.instrumentation:opentelemetry-instrumentation-bom:2.10.0"))

    // OpenTelemetry Core API & SDK
    api("io.opentelemetry:opentelemetry-api")
    api("io.opentelemetry:opentelemetry-sdk")
    api("io.opentelemetry:opentelemetry-sdk-extension-autoconfigure")

    // OTLP Exporter for sending telemetry data
    implementation("io.opentelemetry:opentelemetry-exporter-otlp")

    // Semantic Conventions for standard attribute naming
    implementation("io.opentelemetry.semconv:opentelemetry-semconv:1.28.0-alpha")

    // Logback MDC for trace correlation in logs (alpha version - not in stable BOM)
    implementation("io.opentelemetry.instrumentation:opentelemetry-logback-mdc-1.0:2.10.0-alpha")

    // Micrometer for metrics (managed by Spring Boot BOM)
    api("io.micrometer:micrometer-core")

    // Spring Framework (managed by Spring Boot BOM)
    api("org.springframework:spring-context")
    api("org.springframework.boot:spring-boot-actuator-autoconfigure")

    // Validation API
    api("jakarta.validation:jakarta.validation-api")

    // Reactor Core for reactive support
    api("io.projectreactor:reactor-core")

    // Spring WebFlux for WebClient
    api("org.springframework.boot:spring-boot-starter-webflux")

    // Caffeine cache for idempotency
    api("com.github.ben-manes.caffeine:caffeine:3.1.8")

    // Spring Kafka for DltPublisher
    api("org.springframework.kafka:spring-kafka")

    // Lombok for code generation
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    // Spring Boot Configuration Processor for IDE support and metadata generation
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

    // Logging
    implementation("org.slf4j:slf4j-api:2.0.9")

    // Test
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.0")
    testImplementation("org.assertj:assertj-core:3.25.1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // BlockHound support for reactive testing
    val blockhoundVersion: String by rootProject.extra
    testImplementation("io.projectreactor.tools:blockhound:$blockhoundVersion")
    testImplementation("io.projectreactor:reactor-test:3.7.0")
    testImplementation("io.projectreactor:reactor-core:3.7.0")
}

tasks.test {
    useJUnitPlatform()
    systemProperty("blockhound.enabled", "true")
    jvmArgs(
        "-XX:+EnableDynamicAgentLoading",
        "-XX:+AllowRedefinitionToAddDeleteMethods"
    )
}
