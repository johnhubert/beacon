plugins {
    id("org.springframework.boot")
    id("io.spring.dependency-management")
    java
}

version = "1.0.0"

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation(project(":common:stateful-client"))
    implementation(project(":common:data-model"))
    implementation(project(":common:auth"))
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.6.0")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}
