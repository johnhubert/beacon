plugins {
    application
}

application {
    mainClass.set("com.beacon.tools.congress.cli.CongressCli")
}

dependencies {
    implementation(project(":common:congress-client"))
    implementation(project(":common:data-model"))

    implementation("commons-cli:commons-cli:1.6.0")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.1")
    implementation("com.fasterxml.jackson.core:jackson-annotations:2.17.1")
    implementation("com.google.protobuf:protobuf-java-util:3.25.3")

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.3")
    testImplementation("org.assertj:assertj-core:3.26.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

tasks.test {
    useJUnitPlatform()
}
