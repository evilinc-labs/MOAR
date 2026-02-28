plugins {
    id("fabric-loom")
    id("maven-publish")
}

version = "${property("mod.version")}+${sc.current.version}"
group = property("mod.group") as String

base {
    archivesName = property("mod.id") as String
}

val requiredJava = when {
    sc.current.parsed >= "1.20.5" -> JavaVersion.VERSION_21
    sc.current.parsed >= "1.18" -> JavaVersion.VERSION_17
    sc.current.parsed >= "1.17" -> JavaVersion.VERSION_16
    else -> JavaVersion.VERSION_1_8
}

dependencies {
    minecraft("com.mojang:minecraft:${sc.current.version}")
    mappings("net.fabricmc:yarn:${property("deps.yarn_mappings")}:v2")
    modImplementation("net.fabricmc:fabric-loader:${property("deps.fabric_loader")}")

    // Fabric API
    modImplementation("net.fabricmc.fabric-api:fabric-api:${property("deps.fabric_api")}")

    // Baritone (cabaletta/baritone) is integrated via reflection at runtime —
    // no compile-time dependency needed. Users install it separately.
}

loom {
    runConfigs.all {
        ideConfigGenerated(true)
        runDir = "../../run" // Share run directory between versions
    }
}

java {
    withSourcesJar()
    targetCompatibility = requiredJava
    sourceCompatibility = requiredJava
}

tasks {
    processResources {
        inputs.property("id", project.property("mod.id"))
        inputs.property("name", project.property("mod.name"))
        inputs.property("version", project.property("mod.version"))
        inputs.property("minecraft", project.property("mod.mc_dep"))

        val props = mapOf(
            "id" to project.property("mod.id"),
            "name" to project.property("mod.name"),
            "version" to project.property("mod.version"),
            "minecraft" to project.property("mod.mc_dep")
        )

        filesMatching("fabric.mod.json") { expand(props) }
    }

    // Builds the version into a shared folder in `build/libs/${mod version}/`
    register<Copy>("buildAndCollect") {
        group = "build"
        from(remapJar.map { it.archiveFile }, remapSourcesJar.map { it.archiveFile })
        into(rootProject.layout.buildDirectory.file("libs/${project.property("mod.version")}"))
        dependsOn("build")
    }
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            groupId = "${property("mod.group")}.${property("mod.id")}"
            artifactId = property("mod.id") as String
            version = project.version as String
            from(components["java"])
        }
    }

    repositories {
        // Add your maven repository here if needed
    }
}
