plugins {
    java
    id("net.fabricmc.fabric-loom-remap") version "1.17.19"
    id("ploceus") version "1.17.6"
}

group = "us.z1x"
version = property("mod_version")!!

java.toolchain.languageVersion = JavaLanguageVersion.of(25)

repositories {
    exclusiveContent {
        forRepository { mavenCentral() }
        filter { includeGroup("org.lwjgl") }
    }
    maven("https://maven.taumc.org/releases")
    maven("https://maven.cloverclient.com/releases")
    mavenCentral()
}

configurations.configureEach {
    exclude(group = "org.lwjgl.lwjgl")
}

ploceus {
    setIntermediaryGeneration(2)
}

loom {
    accessWidenerPath = file("src/main/resources/lucerna.classtweaker")
}

dependencies {
    minecraft("com.mojang:minecraft:${property("minecraft_version")}")
    mappings(ploceus.layeredMappings {
        mappings("net.ornithemc:feather-gen2:${property("minecraft_version")}+build.${property("feather_build")}:v2") {
            containsUnpick()
        }
    })
    modImplementation("net.fabricmc:fabric-loader:${property("fabric_version")}")

    modCompileOnly("dev.rdh:argentum:${property("argentum_version")}")
    compileOnly("org.embeddedt.celeritas:celeritas-common:${property("celeritas_version")}")
    compileOnly("org.joml:joml:1.10.5")
    modCompileOnly("pl.tomgirl:pylon:${property("pylon_version")}")
    compileOnly("it.unimi.dsi:fastutil:8.5.9")
    compileOnly("org.slf4j:slf4j-api:2.0.17")
    compileOnly("org.jetbrains:annotations:24.1.0")

    for (dep in listOf(
        "io.github.douira:glsl-transformer:1.0.1",
        "org.antlr:antlr4-runtime:4.10.1",
        "org.anarres:jcpp:1.4.14",
    )) {
        include(implementation(dep) { isTransitive = false })
    }
}

tasks.processResources {
    inputs.property("version", version)
    filesMatching("fabric.mod.json") {
        expand("version" to version)
    }
    from("LICENSE") {
        into("META-INF")
    }
}
