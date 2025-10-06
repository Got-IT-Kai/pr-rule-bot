plugins {
    java
    id("org.springframework.boot") version "3.5.3"
    id("io.spring.dependency-management") version "1.1.7"
    jacoco
    id("jvm-test-suite")
    id("org.sonarqube") version "6.3.1.5724"
}

group = "com.code"
version = "0.0.1-SNAPSHOT"

sonar {
    properties {
        property("sonar.projectKey", "Got-IT-Kai_pr-rule-bot")
        property("sonar.organization", "got-it-kai")
        property("sonar.host.url", "https://sonarcloud.io")
        property("sonar.coverage.jacoco.xmlReportPaths", "build/reports/jacoco/jacocoMergeReport/jacocoMergeReport.xml")
        // Exclude GitHub Actions workflows from analysis
        property("sonar.exclusions", ".github/workflows/**")
    }
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

val springAiVersion by extra("1.0.0")
val grpcVersion by extra("1.75.0")
val jtokkitVersion by extra("1.0.0")
val dotenvVersion by extra("2.3.2")
val mockitoInlineVersion by extra("5.2.0")
val blockhoundVersion by extra("1.0.8.RELEASE")
val mockwebserverVersion by extra("4.12.0")

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.ai:spring-ai-starter-model-ollama")
    implementation("org.springframework.ai:spring-ai-starter-model-vertex-ai-gemini")
    implementation("io.projectreactor:reactor-core")
    implementation("io.projectreactor.netty:reactor-netty-http")
    implementation("com.knuddels:jtokkit:$jtokkitVersion")
    // Required for Vertex AI Gemini - gRPC transport layer
    implementation("io.grpc:grpc-netty-shaded:$grpcVersion")
    implementation("io.github.cdimascio:dotenv-java:$dotenvVersion")

    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    // Removed: spring-ai-advisors-vector-store
    // Reason: ChromaDB vector store not needed for v1.0 (deferred to v2.0 for RAG features)
    // See: Issue #44, docs/release/v1.0-plan.md
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.ai:spring-ai-bom:$springAiVersion")
    }
}

testing {
    suites {
        // Common configuration for all test suites
        withType(JvmTestSuite::class).configureEach {
            useJUnitJupiter()
            dependencies {
                implementation("org.springframework.boot:spring-boot-starter-test")
                implementation("io.projectreactor:reactor-test")
            }
        }

        // Unit tests
        val test by getting(JvmTestSuite::class) {
            dependencies {
                implementation("org.mockito:mockito-inline:$mockitoInlineVersion")
                implementation("io.projectreactor.tools:blockhound:$blockhoundVersion")
                runtimeOnly("org.junit.platform:junit-platform-launcher")
            }
            targets.all {
                testTask.configure {
                    systemProperty("blockhound.enabled", "true")
                    jvmArgs(
                        "-XX:+EnableDynamicAgentLoading",
                        "-XX:+AllowRedefinitionToAddDeleteMethods"
                    )
                }
            }
        }

        // Integration tests
        val integrationTest by registering(JvmTestSuite::class) {
            dependencies {
                implementation(project())
                implementation("com.squareup.okhttp3:mockwebserver:$mockwebserverVersion")
                implementation("org.springframework:spring-webflux")
                implementation("io.projectreactor.netty:reactor-netty-http")
            }
            targets {
                all {
                    testTask.configure {
                        shouldRunAfter(test)
                        systemProperty("blockhound.enabled", "false")
                        jvmArgs("-XX:+EnableDynamicAgentLoading", "-Xshare:off")
                    }
                }
            }
        }
    }
}

tasks.named("sonar") {
    dependsOn(tasks.named("jacocoMergeReport"))
}

tasks.named("check") {
    dependsOn(tasks.named("test"))
    dependsOn(tasks.named("integrationTest"))
}

tasks.named<JacocoReport>("jacocoTestReport") {
    reports {
        xml.required = true
        html.required = true
    }
    dependsOn(tasks.test)
}

tasks.register<JacocoReport>("jacocoMergeReport") {
    dependsOn(tasks.test, tasks.named("integrationTest"))

    executionData(fileTree(layout.buildDirectory.dir("jacoco")) {
        include("*.exec")
    })

    sourceDirectories.setFrom(files(sourceSets.main.get().allSource.srcDirs))
    classDirectories.setFrom(files(sourceSets.main.get().output))

    reports {
        xml.required = true
        html.required = true
    }
}
