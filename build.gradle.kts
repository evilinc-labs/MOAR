plugins {
    id("maven-publish")
}

val isUnobfuscated = sc.current.parsed >= "26.1"

// Use non-remapping Loom for unobfuscated 26.1+, remapping Loom for older versions
if (isUnobfuscated) {
    apply(plugin = "net.fabricmc.fabric-loom")
} else {
    apply(plugin = "fabric-loom")
}

version = "${property("mod.version")}+${sc.current.version}"
group = property("mod.group") as String

base {
    archivesName = property("mod.id") as String
}

repositories {
    maven("https://babbaj.github.io/maven/")
    maven("https://api.modrinth.com/maven") {
        content { includeGroup("maven.modrinth") }
    }
}

val requiredJava = when {
    isUnobfuscated -> JavaVersion.toVersion(25)
    sc.current.parsed >= "1.20.5" -> JavaVersion.VERSION_21
    sc.current.parsed >= "1.18" -> JavaVersion.VERSION_17
    sc.current.parsed >= "1.17" -> JavaVersion.VERSION_16
    else -> JavaVersion.VERSION_1_8
}

dependencies {
    "minecraft"("com.mojang:minecraft:${sc.current.version}")
    if (isUnobfuscated) {
        // MC 26.1+ is unobfuscated — no mappings or remapping needed
        "implementation"("net.fabricmc:fabric-loader:${property("deps.fabric_loader")}")
        "implementation"("net.fabricmc.fabric-api:fabric-api:${property("deps.fabric_api")}")
    } else {
        "mappings"("net.fabricmc:yarn:${property("deps.yarn_mappings")}:v2")
        "modImplementation"("net.fabricmc:fabric-loader:${property("deps.fabric_loader")}")
        "modImplementation"("net.fabricmc.fabric-api:fabric-api:${property("deps.fabric_api")}")
    }

    // SQLite JDBC — bundled into the mod jar
    val sqlite = "implementation"("org.xerial:sqlite-jdbc:3.49.1.0")!!
    "include"(sqlite)

    // Compile the optional Baritone native-call compatibility mixin.
    "compileOnly"("dev.babbaj:nether-pathfinder:1.4.1")

    // Xaero's World Map is optional at runtime. Compile against the matching
    // Fabric build so Loom can remap its Minecraft references for each target.
    val xaeroWorldMapVersion = when (sc.current.version) {
        "1.21.4" -> "dP0Nv0XF"
        "1.21.5" -> "EiwSu1fH"
        "1.21.8" -> "16jeKzeM"
        "1.21.10" -> "dJSazz1g"
        "1.21.11" -> "uuPYtgMh"
        "26.1.1" -> "ZpUBkoFm" // Xaero's 26.1.2 build supports 26.1.x.
        "26.2" -> "fabric-26.2-1.47.2"
        "26.3" -> "VM8lNSSM"
        else -> error("No Xaero's World Map compile dependency for ${sc.current.version}")
    }
    if (isUnobfuscated) {
        "compileOnly"("maven.modrinth:NcUtCpym:$xaeroWorldMapVersion")
    } else {
        "modCompileOnly"("maven.modrinth:NcUtCpym:$xaeroWorldMapVersion")
    }

    "testImplementation"("org.junit.jupiter:junit-jupiter:5.11.4")
    "testRuntimeOnly"("org.junit.platform:junit-platform-launcher:1.11.4")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

extensions.configure<net.fabricmc.loom.api.LoomGradleExtensionAPI> {
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
        inputs.property("loader", project.property("deps.fabric_loader"))
        inputs.property("java", requiredJava.majorVersion)

        val props = mapOf(
            "id" to project.property("mod.id"),
            "name" to project.property("mod.name"),
            "version" to project.property("mod.version"),
            "minecraft" to project.property("mod.mc_dep"),
            "loader" to project.property("deps.fabric_loader"),
            "java" to requiredJava.majorVersion
        )

        filesMatching("fabric.mod.json") { expand(props) }
    }

    // Builds the version into a shared folder in `build/libs/${mod version}/`
    register<Copy>("buildAndCollect") {
        group = "build"
        if (isUnobfuscated) {
            from(named("jar").map { (it as org.gradle.jvm.tasks.Jar).archiveFile })
        } else {
            from(named("remapJar").map { (it as org.gradle.jvm.tasks.Jar).archiveFile },
                named("remapSourcesJar").map { (it as org.gradle.jvm.tasks.Jar).archiveFile })
        }
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
        // Template
    }
}
