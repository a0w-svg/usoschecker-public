plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
}

kotlin {
    androidLibrary {
        namespace = "pl.vistula.checker.shared"
        compileSdk = 36
        minSdk = 31
    }
    jvm("desktop")

    // Android and desktop are both JVM targets, so the domain code shares one source set that may
    // use java.time and org.json. A pure commonMain port (kotlinx-datetime / serialization) is a
    // later step that only becomes necessary if a non-JVM target such as iOS is added.
    applyDefaultHierarchyTemplate {
        common {
            group("jvmCommon") {
                withAndroidTarget()
                withJvm()
            }
        }
    }

    sourceSets {
        val jvmCommonMain by getting {
            dependencies {
                // Android ships org.json in the platform; desktop gets the real library below.
                compileOnly("org.json:json:20240303")
            }
        }
        val desktopMain by getting {
            dependencies {
                implementation("org.json:json:20240303")
            }
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
