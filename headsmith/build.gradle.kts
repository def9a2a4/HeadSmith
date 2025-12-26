plugins {
    `java`
    id("com.gradleup.shadow") version "9.3.0"
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
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    implementation("org.bstats:bstats-bukkit:3.1.0")
}

// Generate manifest of bundled head files for runtime discovery
val generateHeadsManifest by tasks.registering {
    val headsDir = file("src/main/resources/heads")
    val manifestFile = layout.buildDirectory.file("generated-resources/heads-manifest.txt")
    inputs.dir(headsDir)
    outputs.file(manifestFile)
    doLast {
        val outFile = manifestFile.get().asFile
        outFile.parentFile.mkdirs()
        val headFiles = fileTree(headsDir)
            .matching { include("**/*.yml") }
            .files
            .map { it.relativeTo(file("src/main/resources")).path.replace("\\", "/") }
            .sorted()
        outFile.writeText(headFiles.joinToString("\n"))
    }
}

sourceSets {
    main {
        resources {
            srcDir(layout.buildDirectory.dir("generated-resources"))
        }
    }
}

tasks {
    processResources {
        dependsOn(generateHeadsManifest)
        val props = mapOf("version" to version)
        inputs.properties(props)
        filteringCharset = "UTF-8"
        filesMatching("plugin.yml") {
            expand(props)
        }
    }

    jar {
        archiveBaseName.set("HeadSmith")
        manifest {
            attributes["paperweight-mappings-namespace"] = "mojang"
        }
    }

    shadowJar {
        archiveBaseName.set("HeadSmith")
        archiveClassifier.set("")
        manifest {
            attributes["paperweight-mappings-namespace"] = "mojang"
        }
        relocate("org.bstats", "${project.group}.bstats")
        mergeServiceFiles()
    }
}
