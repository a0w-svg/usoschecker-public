import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":shared"))
    implementation(compose.desktop.currentOs)
    implementation(compose.runtime)
    implementation(compose.foundation)
    implementation(compose.material3)
    implementation(compose.ui)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.11.0")
    implementation("org.json:json:20260814")
}

compose.desktop {
    application {
        mainClass = "pl.vistula.checker.desktop.MainKt"
        providers.gradleProperty("desktopJavaHome").orNull?.let { javaHome = it }
        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe, TargetFormat.Deb, TargetFormat.Dmg)
            packageName = "VistulaChecker"
            packageVersion = "1.5.0"
            description = "Vistula USOS Checker Desktop (Compose Multiplatform)"
            vendor = "Vistula Checker"
            modules("java.net.http", "java.prefs", "java.sql", "jdk.crypto.ec")
            windows {
                perUserInstall = true
                dirChooser = true
                menuGroup = "Vistula Checker"
                upgradeUuid = "4c949fa7-868b-4f3a-80e9-71c78c455768"
            }
        }
    }
}

