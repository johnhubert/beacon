import org.gradle.jvm.toolchain.JavaLanguageVersion

plugins {
    id("org.springframework.boot")
    id("io.spring.dependency-management")
    java
}

group = "com.beacon.services"

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.kafka:spring-kafka")
    implementation(project(":common:data-model"))
    implementation(project(":common:stateful-client"))
    implementation(project(":common:congress-client"))

    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

tasks.test {
    useJUnitPlatform()
}
