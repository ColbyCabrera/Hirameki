// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2025 David Allison <davidallisongithub@gmail.com>
// SPDX-FileCopyrightText: Copyright (c) 2026 Colby Cabrera <colbycabrera.wd@gmail.com>

import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.ichi2.anki.libanki.testutils"
    compileSdk =
        libs.versions.compileSdk
            .get()
            .toInt()

    defaultConfig {
        minSdk =
            libs.versions.minSdk
                .get()
                .toInt()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        compilerOptions {
            jvmTarget = JvmTarget.JVM_17
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/LICENSE.md"
            excludes += "/META-INF/LICENSE-notice.md"
        }
    }
}

dependencies {
    implementation(project(":common"))
    implementation(project(":libanki"))

    // Backend libraries
    implementation(libs.protobuf.kotlin.lite) // This is required when loading from a file

    val localProperties = Properties()
    if (project.rootProject.file("local.properties").exists()) {
        localProperties.load(project.rootProject.file("local.properties").inputStream())
    }
    if (localProperties["local_backend"] == "true") {
        // ../Anki-Android-Backend, beside the repository.
        // 0.1.76+: local artifacts have no Maven metadata, so both the core JAR and the Android AAR are needed
        val backendCheckout = rootProject.file("../Anki-Android-Backend")
        implementation(
            files(
                backendCheckout.resolve("rsdroid/build/libs/rsdroid.jar"),
                backendCheckout.resolve("rsdroid-android/build/outputs/aar/rsdroid-android-release.aar"),
            ),
        )
        implementation(files(backendCheckout.resolve("rsdroid-testing/build/libs/rsdroid-testing.jar")))
    } else {
        implementation(libs.ankiBackend.backend)
        implementation(libs.ankiBackend.testing)
    }

    implementation(libs.jakewharton.timber)
    implementation(libs.junit.vintage.engine)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.test)

    implementation(libs.androidx.sqlite.framework)
}
