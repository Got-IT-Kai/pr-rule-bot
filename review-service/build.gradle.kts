plugins {
    java
    id("org.springframework.boot") version "3.5.3"
    id("io.spring.dependency-management") version "1.1.7"
    `jvm-test-suite`
    jacoco
}

group = "com.code"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // Platform commons
    implementation(project(":platform-commons"))

    // Shared domain events
    implementation(project(":shared-events"))

    // Spring Kafka for event-driven communication
    implementation("org.springframework.kafka:spring-kafka")

    // Spring Boot WebFlux for reactive AI processing
    implementation("org.springframework.boot:spring-boot-starter-webflux")

    // Spring AI
    implementation("org.springframework.ai:spring-ai-starter-model-ollama:1.0.0")
    implementation("org.springframework.ai:spring-ai-starter-model-vertex-ai-gemini:1.0.0")

    // Required for Vertex AI Gemini - gRPC transport layer
    implementation("io.grpc:grpc-netty-shaded:1.75.0")

    // JTokkit for token counting
    implementation("com.knuddels:jtokkit:1.1.0")

    // Jackson for JSON processing
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")



    // Validation
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // Lombok
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")
}

testing {
    suites {
        val test by getting(JvmTestSuite::class) {
            useJUnitJupiter()
        }
    }
}

tasks.test {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)

    executionData.setFrom(fileTree(layout.buildDirectory.dir("jacoco")).include("*.exec"))

    reports {
        xml.required.set(true)
        html.required.set(true)
    }

    classDirectories.setFrom(
        files(classDirectories.files.map {
            fileTree(it) {
                exclude(
                    "**/ReviewServiceApplication.class",
                    "**/infrastructure/config/**",
                    "**/infrastructure/adapter/inbound/event/**",
                    "**/infrastructure/adapter/outbound/event/**",
                    "**/infrastructure/adapter/outbound/ai/*AiClient.class",
                    "**/infrastructure/adapter/outbound/ai/*Properties.class",
                    "**/infrastructure/adapter/outbound/ai/AiProvider.class"
                )
            }
        })
    )
}

tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.test)

    executionData.setFrom(fileTree(layout.buildDirectory.dir("jacoco")).include("*.exec"))

    classDirectories.setFrom(
        files(classDirectories.files.map {
            fileTree(it) {
                exclude(
                    "**/ReviewServiceApplication.class",
                    "**/infrastructure/config/**",
                    "**/infrastructure/adapter/inbound/event/**",
                    "**/infrastructure/adapter/outbound/event/**",
                    "**/infrastructure/adapter/outbound/ai/*AiClient.class",
                    "**/infrastructure/adapter/outbound/ai/*Properties.class",
                    "**/infrastructure/adapter/outbound/ai/AiProvider.class"
                )
            }
        })
    )

    violationRules {
        rule {
            limit {
                minimum = "0.70".toBigDecimal()
            }
        }
    }
}
