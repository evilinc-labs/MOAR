pluginManagement {
    repositories {
        mavenLocal()
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.fabricmc.net/")
        maven("https://maven.kikugie.dev/snapshots") { name = "KikuGie Snapshots" }
    }
}

plugins {
    id("dev.kikugie.stonecutter") version "0.8.3"
}

val javaMajor = JavaVersion.current().majorVersion.toInt()
val includeJava25Targets = providers.gradleProperty("moar.includeJava25Targets")
    .orNull
    ?.toBooleanStrictOrNull()
    ?: (javaMajor >= 25)

if (includeJava25Targets && javaMajor < 25) {
    throw GradleException("Minecraft 26.x requires JDK 25+. "
        + "Run Gradle with Java 25+ or omit -Pmoar.includeJava25Targets=true.")
}

val minecraftVersions = buildList {
    add("1.21.4")
    add("1.21.5")
    add("1.21.8")
    add("1.21.10")
    add("1.21.11")
    if (includeJava25Targets) {
        add("26.1.1")
        add("26.2")
    }
}

stonecutter {
    create(rootProject) {
        versions(*minecraftVersions.toTypedArray())
        vcsVersion = "1.21.4"
    }
}

rootProject.name = "moar"
