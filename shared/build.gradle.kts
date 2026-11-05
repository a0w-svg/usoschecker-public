plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
}

@OptIn(org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi::class)
kotlin {
    android {
        namespace = "pl.vistula.checker.shared"
        compileSdk = 37
        minSdk = 31
        withHostTest {}
    }
    jvm("desktop")

    // Android and desktop are both JVM targets, so the domain code shares one source set that may
    // use java.time and org.json. A pure commonMain port (kotlinx-datetime / serialization) is a
    // later step that only becomes necessary if a non-JVM target such as iOS is added.
    applyDefaultHierarchyTemplate {
        common {
            group("jvmCommon") {
                withCompilations { true }
            }
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(libs.compose.runtime)
            api(libs.compose.foundation)
            api(libs.compose.material3)
            api(libs.compose.ui)
        }
        val jvmCommonMain = getByName("jvmCommonMain") {
            dependencies {
                // Android ships org.json in the platform; desktop gets the real library below.
                compileOnly(libs.json)
            }
        }
        val desktopMain = getByName("desktopMain") {
            dependencies {
                implementation(libs.json)
                api(compose.desktop.currentOs)
            }
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
