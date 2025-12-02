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
    // Platform commons for correlation ID
    implementation(project(":platform-commons"))

    // Shared domain events
    implementation(project(":shared-events"))

    // Spring Kafka for event publishing
    implementation("org.springframework.kafka:spring-kafka")

    // Spring Boot WebFlux for reactive non-blocking I/O
    implementation("org.springframework.boot:spring-boot-starter-webflux")

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

        val integrationTest by registering(JvmTestSuite::class) {
            dependencies {
                implementation(project())
                implementation(project(":shared-events"))
                implementation("org.springframework.boot:spring-boot-starter-test")
                implementation("org.springframework.boot:spring-boot-starter-webflux")
                implementation("io.projectreactor:reactor-test")
                implementation("com.squareup.okhttp3:mockwebserver:4.12.0")

                // Testcontainers for integration testing
                implementation(platform("org.testcontainers:testcontainers-bom:${rootProject.extra["testcontainersVersion"]}"))
                implementation("org.testcontainers:testcontainers")
                implementation("org.testcontainers:junit-jupiter")
                implementation("org.testcontainers:kafka")

                // REST Assured for API testing
                implementation("io.rest-assured:rest-assured:${rootProject.extra["restAssuredVersion"]}")
                implementation("io.rest-assured:json-path:${rootProject.extra["restAssuredVersion"]}")

                // Awaitility for async assertions
                implementation("org.awaitility:awaitility:${rootProject.extra["awaitilityVersion"]}")
            }

            targets {
                all {
                    testTask.configure {
                        shouldRunAfter(test)
                        // Testcontainers configuration for Colima compatibility
                        environment("TESTCONTAINERS_RYUK_DISABLED", "true")
                    }
                }
            }
        }
    }
}

tasks.named("check") {
    dependsOn(testing.suites.named("integrationTest"))
}

tasks.test {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test, tasks.named("integrationTest"))

    executionData.setFrom(fileTree(layout.buildDirectory.dir("jacoco")).include("*.exec"))

    reports {
        xml.required.set(true)
        html.required.set(true)
    }

    classDirectories.setFrom(
        files(classDirectories.files.map {
            fileTree(it) {
                exclude(
                    "**/WebhookServiceApplication.class",
                    "**/infrastructure/config/**",
                    "**/domain/model/**"
                )
            }
        })
    )
}

tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.test, tasks.named("integrationTest"))

    executionData.setFrom(fileTree(layout.buildDirectory.dir("jacoco")).include("*.exec"))

    classDirectories.setFrom(
        files(classDirectories.files.map {
            fileTree(it) {
                exclude(
                    "**/WebhookServiceApplication.class",
                    "**/infrastructure/config/**",
                    "**/domain/model/**"
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
