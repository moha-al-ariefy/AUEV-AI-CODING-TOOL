plugins {
    id("java")
    // I bumped Kotlin to 2.0.0 because 2025.3 loves the new stuff
    id("org.jetbrains.kotlin.jvm") version "2.0.0"
    // UPDATED: I bumped this to 2.2.1 so Gradle stops yelling about "Outdated Plugin"
    id("org.jetbrains.intellij.platform") version "2.2.1"
    // Invoking the spirit of Compose (Updated for Kotlin 2.0)
    id("org.jetbrains.compose") version "1.6.11"
    // This compiler plugin is mandatory now for Compose + Kotlin 2.0
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.0"
}

group = "com.hackathon"
version = "0.7-2026"

repositories {
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    google()

    // The new platform plugin needs these repos to find the IntelliJ artifacts
    intellijPlatform {
        defaultRepositories()
    }
}

// Configuring the new Platform Plugin (It's pickier than the old one)
intellijPlatform {
    pluginConfiguration {
        name = "AUEV AI Coding Tool"
    }

    // This is where we tell it to target 2025.3
    // I kept this disabled because it causes headaches with search indexing
    buildSearchableOptions = false

    // We want the latest and greatest IDEA Community
    instrumentCode = true
}

dependencies {
    // I need these to draw pretty rectangles.
    // CRITICAL FIX: I excluded the native Skiko runtimes here.
    // IntelliJ already has Skiko loaded. If we bring our own, they fight and crash (UnsatisfiedLinkError).
    implementation(compose.desktop.currentOs) {
        exclude(group = "org.jetbrains.skiko", module = "skiko-awt-runtime-windows-x64")
        exclude(group = "org.jetbrains.skiko", module = "skiko-awt-runtime-macos-x64")
        exclude(group = "org.jetbrains.skiko", module = "skiko-awt-runtime-linux-x64")
    }

    // The new way to define the IDE version
    intellijPlatform {
        create("IC", "2024.3") // 2024.3 is the bedrock for 2025 builds right now
        bundledPlugin("com.intellij.java")

        instrumentationTools()        // this should stop the crying

    }
}

tasks {
    // 2025 runs on Java 21 (JBR 21). Resistance is futile.
    withType<JavaCompile> {
        sourceCompatibility = "21"
        targetCompatibility = "21"
    }

    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        }
    }

    patchPluginXml {
        sinceBuild.set("243") // 243 is the build code for 2024.3/2025 precursors
        untilBuild.set("255.*") // To infinity and beyond
    }
}