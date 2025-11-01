import java.util.Properties
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.tasks.Sync

plugins {
    application
}

application {
    mainClass.set("com.beacon.tools.congress.cli.CongressCli")
    applicationName = "congress-cli"
}

dependencies {
    implementation(project(":common:congress-client"))
    implementation(project(":common:data-model"))

    implementation("commons-cli:commons-cli:1.6.0")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.1")
    implementation("com.fasterxml.jackson.core:jackson-annotations:2.17.1")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.17.1")
    implementation("com.google.protobuf:protobuf-java-util:3.25.3")

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.3")
    testImplementation("org.assertj:assertj-core:3.26.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

val integrationTestSourceSet = sourceSets.create("integrationTest") {
    compileClasspath += sourceSets.main.get().output + sourceSets.test.get().compileClasspath
    runtimeClasspath += output + compileClasspath + sourceSets.test.get().runtimeClasspath
}

configurations[integrationTestSourceSet.implementationConfigurationName].extendsFrom(configurations.testImplementation.get())
configurations[integrationTestSourceSet.runtimeOnlyConfigurationName].extendsFrom(configurations.testRuntimeOnly.get())

tasks.test {
    useJUnitPlatform()
    // Ensure fast feedback: CLI integration tests live in a separate source set and only run on demand.
}

fun ProviderFactory.resolveCongressApiKey(): Provider<String> {
    val envProvider = environmentVariable("CONGRESS_API_KEY")
    val gradleProvider = gradleProperty("CONGRESS_API_KEY")
    val fileProvider = provider {
        val file = rootProject.file("gradle.properties")
        if (file.exists()) {
            Properties().apply {
                file.inputStream().use(::load)
            }.getProperty("CONGRESS_API_KEY", "")
        } else {
            ""
        }
    }
    return envProvider.orElse(gradleProvider).orElse(fileProvider)
}

val integrationFlag = providers.gradleProperty("integrationTests")
    .map { it.equals("true", ignoreCase = true) }
    .orElse(false)
val cliCongressApiKey = providers.resolveCongressApiKey()

val integrationTest by tasks.registering(Test::class) {
    description = "Runs Congress CLI integration tests that hit the live Congress.gov API."
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    testClassesDirs = integrationTestSourceSet.output.classesDirs
    classpath = integrationTestSourceSet.runtimeClasspath
    useJUnitPlatform()
    onlyIf {
        if (!integrationFlag.get()) {
            logger.lifecycle("Skipping congress-cli integration tests (enable with -PintegrationTests=true)")
            return@onlyIf false
        }
        if (cliCongressApiKey.orNull.isNullOrBlank()) {
            logger.warn("Skipping congress-cli integration tests because CONGRESS_API_KEY is not set.")
            return@onlyIf false
        }
        true
    }
    systemProperty("CONGRESS_API_KEY", cliCongressApiKey.get())
}

tasks.check {
    dependsOn(integrationTest)
}

val installDist by tasks.existing(Sync::class)

val assembleToolDistribution by tasks.registering(Sync::class) {
    dependsOn(installDist)

    val applicationName = project.name
    val installDir = layout.buildDirectory.dir("install/$applicationName")

    into(rootProject.layout.buildDirectory.dir("tools"))

    from(installDir.map { it.dir("lib") }) {
        into("lib")
    }

    from(installDir.map { it.file("bin/$applicationName") }) {
        into("scripts")
        rename { _ -> "${applicationName}.sh" }
    }
}

tasks.named("assemble") {
    dependsOn(assembleToolDistribution)
}
