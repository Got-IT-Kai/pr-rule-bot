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
    // Internal modules
    implementation(project(":platform-commons"))
    implementation(project(":shared-events"))

    // Spring Kafka for event-driven communication
    implementation("org.springframework.kafka:spring-kafka")

    // Spring Boot WebFlux for reactive GitHub API calls
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
                    "**/IntegrationServiceApplication.class",
                    "**/infrastructure/config/**",
                    "**/domain/model/**"
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
                    "**/IntegrationServiceApplication.class",
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

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootBuildImage>("bootBuildImage") {
    imagePlatform.set("linux/amd64")
}
