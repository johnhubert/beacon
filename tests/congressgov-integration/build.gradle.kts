import java.util.Properties

plugins {
    java
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

dependencies {
    testImplementation(project(":common:congress-client"))
    testImplementation(project(":common:data-model"))

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.3")
    testImplementation("org.assertj:assertj-core:3.26.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

fun ProviderFactory.resolveCongressApiKey(): Provider<String> {
    val envProvider = environmentVariable("API_CONGRESS_GOV_KEY")
    val gradleProvider = gradleProperty("API_CONGRESS_GOV_KEY")
    val fileProvider = provider {
        val file = rootProject.file("gradle.properties")
        if (file.exists()) {
            Properties().apply {
                file.inputStream().use(::load)
            }.getProperty("API_CONGRESS_GOV_KEY", "")
        } else {
            ""
        }
    }
    return envProvider.orElse(gradleProvider).orElse(fileProvider)
}

val congressApiKey = providers.resolveCongressApiKey()
val congressNumberProp = providers.gradleProperty("CONGRESS_NUMBER").orElse("118")
val integrationFlag = providers.gradleProperty("integrationTests")
    .map { it.equals("true", ignoreCase = true) }
    .orElse(false)

val testTask = tasks.test {
    useJUnitPlatform()
    outputs.upToDateWhen { false }
    onlyIf {
        if (!integrationFlag.get()) {
            logger.lifecycle("Skipping Congress.gov integration tests (enable with -PintegrationTests=true)")
            return@onlyIf false
        }
        if (congressApiKey.get().isBlank()) {
            logger.warn("Skipping Congress.gov integration tests because API_CONGRESS_GOV_KEY is not set")
            return@onlyIf false
        }
        true
    }
    systemProperty("API_CONGRESS_GOV_KEY", congressApiKey.get())
    systemProperty("CONGRESS_NUMBER", congressNumberProp.get())
}

tasks.register("runCongressGovTests") {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Runs the Congress.gov integration tests if credentials are available"
    dependsOn(testTask)
}
