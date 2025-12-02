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
    // Test
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("com.networknt:json-schema-validator:1.5.0")
    testImplementation("com.fasterxml.jackson.core:jackson-databind:2.17.0")
    testImplementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.17.0")
}

tasks.test {
    useJUnitPlatform()
}
