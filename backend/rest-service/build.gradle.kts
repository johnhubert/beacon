import org.gradle.jvm.toolchain.JavaLanguageVersion

plugins {
    id("org.springframework.boot")
    id("io.spring.dependency-management")
    java
}

group = "com.beacon"

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-validation")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

tasks.test {
    useJUnitPlatform()
}
