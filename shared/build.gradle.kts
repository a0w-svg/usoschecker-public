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

    sourceSets {
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

