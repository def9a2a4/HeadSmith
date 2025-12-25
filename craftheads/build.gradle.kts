plugins {
    `java`
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "anon.def9a2a4"
version = "0.1.0"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
    maven {
        name = "papermc"
        url = uri("https://repo.papermc.io/repository/maven-public/")
    }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.8-R0.1-SNAPSHOT")
    implementation("org.bstats:bstats-bukkit:3.1.0")
}

tasks {
    processResources {
        val props = mapOf("version" to version)
        inputs.properties(props)
        filteringCharset = "UTF-8"
        filesMatching("plugin.yml") {
            expand(props)
        }
    }

    jar {
        archiveBaseName.set("CraftHeads")
        manifest {
            attributes["paperweight-mappings-namespace"] = "mojang"
        }
    }

    named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
        archiveBaseName.set("CraftHeads")
        archiveClassifier.set("")
        manifest {
            attributes["paperweight-mappings-namespace"] = "mojang"
        }
        configurations = listOf(project.configurations.runtimeClasspath.get())
        dependencies { exclude { it.moduleGroup != "org.bstats" } }
        relocate("org.bstats", project.group.toString())
    }
}
