import org.gradle.api.GradleException
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.toolchain.JavaLanguageVersion

plugins {
    base
    id("org.springframework.boot") version "3.5.7" apply false
    id("io.spring.dependency-management") version "1.1.7" apply false
    id("com.google.protobuf") version "0.9.4" apply false
}

allprojects {
    version = "0.0.1-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}

subprojects {
    plugins.withType(JavaPlugin::class.java) {
        extensions.configure(JavaPluginExtension::class.java) {
            toolchain.languageVersion.set(JavaLanguageVersion.of(21))
        }
    }

    tasks.withType(Test::class.java).configureEach {
        useJUnitPlatform()
    }
}

val congressCliAvailable = file("tools/congress-cli").isDirectory

if (congressCliAvailable) {
    val buildTools = tasks.register("buildTools") {
        dependsOn(":tools:congress-cli:assembleToolDistribution")
    }

    tasks.named("build") {
        dependsOn(buildTools)
    }
} else {
    tasks.register("buildTools") {
        group = "build"
        description = "Builds the congress CLI distribution when the tools/congress-cli module is available."
        doLast {
            throw GradleException("The tools/congress-cli module is not available in this environment.")
        }
    }
}

tasks.register("prepareDockerEnv") {
    group = "docker"
    description = "Generates environment variables for docker-compose from gradle.properties."
    doLast {
        val apiKey = findProperty("API_CONGRESS_GOV_KEY")?.toString()?.takeIf { it.isNotBlank() }
                ?: throw GradleException("API_CONGRESS_GOV_KEY is not set. Add it to gradle.properties to run docker-compose.")

        val outputFile = layout.projectDirectory.file("build/docker/ingest-usa-fed.env").asFile
        outputFile.parentFile.mkdirs()
        val content = buildString {
            appendLine("CONGRESS_API_KEY=$apiKey")
            appendLine("API_CONGRESS_GOV_KEY=$apiKey")
        }
        outputFile.writeText(content)
        logger.lifecycle("Wrote docker-compose env file to ${outputFile.relativeTo(layout.projectDirectory.asFile)}")
    }
}
